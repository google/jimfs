package com.google.jimfs.internal;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.jimfs.AttributeViews;
import com.google.jimfs.attribute.AttributeProvider;
import com.google.jimfs.attribute.AttributeStore;
import com.google.jimfs.common.IoSupplier;

import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * @author Colin Decker
 */
final class AttributeService {

  private static final String ALL_ATTRIBUTES = "*";

  private final AttributeProviderRegistry attributeProviders;

  public AttributeService(AttributeViews views) {
    this(new AttributeProviderRegistry(views));
  }

  public AttributeService(AttributeProviderRegistry attributeProviders) {
    this.attributeProviders = attributeProviders;
  }

  /**
   * Implements {@link FileSystem#supportedFileAttributeViews()}.
   */
  public ImmutableSet<String> supportedFileAttributeViews() {
    return attributeProviders.getSupportedViews();
  }

  /**
   * Implements {@link FileStore#supportsFileAttributeView(Class)}.
   */
  public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
    return attributeProviders.getSupportedViewTypes().contains(type);
  }

  /**
   * Sets all initial attributes for the given file, including the given attributes if possible.
   */
  public void setInitialAttributes(File file, FileAttribute<?>... attrs) {
    for (AttributeProvider provider : attributeProviders.getProviders()) {
      provider.setInitial(file);
    }

    for (FileAttribute<?> attr : attrs) {
      setAttributeInternal(file, attr.name(), attr.value(), true);
    }
  }

  /**
   * Copies the file times of the given file to the given copy file.
   */
  public void copyBasicAttributes(File file, File copy) {
    copy.setCreationTime(file.getCreationTime());
    copy.setLastAccessTime(file.getLastAccessTime());
    copy.setLastModifiedTime(file.getLastModifiedTime());
  }

  /**
   * Copies the attributes of the given file to the given copy file.
   */
  public void copyAttributes(File file, File copy) {
    copyBasicAttributes(file, copy);
    for (String attribute : file.getAttributeKeys()) {
      copy.setAttribute(attribute, file.getAttribute(attribute));
    }
  }

  /**
   * Gets the value of the given attribute for the given file. {@code attribute} must be of the form
   * "view:attribute" or "attribute".
   */
  public <V> V getAttribute(File file, String attribute) {
    String view = getViewName(attribute);
    String attr = getSingleAttribute(attribute);
    return getAttribute(file, view, attr);
  }

  /**
   * Gets the value of the given attribute for the given view and file. Neither view nor file may
   * have a ':' character.
   */
  @SuppressWarnings("unchecked")
  public <V> V getAttribute(File file, String view, String attribute) {
    for (AttributeProvider provider : attributeProviders.getProviders(view)) {
      if (provider.isGettable(file, attribute)) {
        return (V) provider.get(file, attribute);
      }
    }

    throw new IllegalArgumentException("attribute not found: " + attribute);
  }

  /**
   * Sets the value of the given attribute to the given value for the given file.
   */
  public void setAttribute(File file, String attribute, Object value) {
    setAttributeInternal(file, attribute, value, false);
  }

  /**
   * Sets the value of the given attribute to the given value for the given view and file.
   */
  public void setAttribute(File file, String view, String attribute, Object value) {
    setAttributeInternal(file, view, attribute, value, false);
  }

  private void setAttributeInternal(File file, String attribute, Object value, boolean create) {
    String view = getViewName(attribute);
    String attr = getSingleAttribute(attribute);
    setAttributeInternal(file, view, attr, value, create);
  }

  private void setAttributeInternal(
      File file, String view, String attribute, Object value, boolean create) {
    for (AttributeProvider provider : attributeProviders.getProviders(view)) {
      if (provider.isSettable(file, attribute)) {
        if (create && !provider.isSettableOnCreate(attribute)) {
          throw new UnsupportedOperationException(
              "cannot set attribute '" + view + ":" + attribute + "' during file creation");
        }

        ImmutableSet<Class<?>> acceptedTypes = provider.acceptedTypes(attribute);
        boolean validType = false;
        for (Class<?> type : acceptedTypes) {
          if (type.isInstance(value)) {
            validType = true;
            break;
          }
        }

        if (validType) {
          provider.set(file, attribute, value);
          return;
        } else {
          Object acceptedTypeMessage = acceptedTypes.size() == 1
              ? acceptedTypes.iterator().next()
              : "one of " + acceptedTypes;
          throw new IllegalArgumentException("invalid type " + value.getClass()
              + " for attribute '" + view + ":" + attribute + "': should be "
              + acceptedTypeMessage);
        }
      }
    }

    throw new IllegalArgumentException("cannot set attribute '" + view + ":" + attribute + "'");
  }

  /**
   * Returns an attribute view of the given type for the given file provider, or {@code null} if the
   * view type is not supported.
   */
  @Nullable
  public <V extends FileAttributeView> V getFileAttributeView(
      IoSupplier<? extends AttributeStore> supplier, Class<V> type) {
    if (supportsFileAttributeView(type)) {
      return attributeProviders.getViewProvider(type).getView(supplier);
    }

    return null;
  }

  /**
   * Implements {@link Files#readAttributes(Path, String, LinkOption...)}.
   */
  public ImmutableMap<String, Object> readAttributes(File file, String attributes) {
    String view = getViewName(attributes);
    List<String> attrs = getAttributeNames(attributes);

    if (attrs.size() > 1 && attrs.contains(ALL_ATTRIBUTES)) {
      // attrs contains * and other attributes
      throw new IllegalArgumentException("invalid attributes: " + attributes);
    }

    Map<String, Object> result = new HashMap<>();
    if (attrs.size() == 1 && attrs.contains(ALL_ATTRIBUTES)) {
      // for 'view:*' format, get all keys for all providers for the view
      for (AttributeProvider provider : attributeProviders.getProviders(view)) {
        provider.readAll(file, result);
      }
    } else {
      // for 'view:attr1,attr2,etc'
      for (String attr : attrs) {
        boolean found = false;
        for (AttributeProvider provider : attributeProviders.getProviders(view)) {
          if (provider.isGettable(file, attr)) {
            result.put(attr, provider.get(file, attr));
            found = true;
            break;
          }
        }

        if (!found) {
          throw new IllegalArgumentException("invalid attribute for view '" + view + "': " + attr);
        }
      }
    }

    return ImmutableMap.copyOf(result);
  }

  /**
   * Returns attributes of the given file as an object of the given type.
   *
   * @throws UnsupportedOperationException if the given attributes type is not supported
   */
  public <A extends BasicFileAttributes> A readAttributes(File file, Class<A> type) {
    if (attributeProviders.getSupportedAttributesTypes().contains(type)) {
      return attributeProviders.getReader(type).read(file);
    }

    throw new UnsupportedOperationException("unsupported attributes type: " + type);
  }

  private static String getViewName(String attribute) {
    int separatorIndex = attribute.indexOf(':');

    if (separatorIndex == -1) {
      return "basic";
    }

    // separator must not be at the start or end of the string or appear more than once
    if (separatorIndex == 0
        || separatorIndex == attribute.length() - 1
        || attribute.indexOf(':', separatorIndex + 1) != -1) {
      throw new IllegalArgumentException("illegal attribute format: " + attribute);
    }

    return attribute.substring(0, separatorIndex);
  }

  private static final Splitter ATTRIBUTE_SPLITTER = Splitter.on(',');

  private static ImmutableList<String> getAttributeNames(String attributes) {
    int separatorIndex = attributes.indexOf(':');
    String attributesPart = attributes.substring(separatorIndex + 1);

    return ImmutableList.copyOf(ATTRIBUTE_SPLITTER.split(attributesPart));
  }

  private static String getSingleAttribute(String attribute) {
    ImmutableList<String> attributeNames = getAttributeNames(attribute);

    if (attributeNames.size() != 1 || ALL_ATTRIBUTES.equals(attributeNames.get(0))) {
      throw new IllegalArgumentException("must specify a single attribute: " + attribute);
    }

    return attributeNames.get(0);
  }
}
