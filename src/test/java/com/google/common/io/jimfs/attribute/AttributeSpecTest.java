package com.google.common.io.jimfs.attribute;

import static org.truth0.Truth.ASSERT;

import org.junit.Test;

/**
 * @author Colin Decker
 */
public class AttributeSpecTest {

  @Test
  public void testAttributeSpecs() {
    AttributeSpec foo = AttributeSpec.unsettable("foo", String.class);
    AttributeSpec bar = AttributeSpec.settable("bar", Long.class);
    AttributeSpec baz = AttributeSpec.settableOnCreate("baz", Integer.class);

    checkAttributeSpec(foo, "foo", String.class, false, false);
    checkAttributeSpec(bar, "bar", Long.class, true, false);
    checkAttributeSpec(baz, "baz", Integer.class, true, true);
  }

  private static void checkAttributeSpec(AttributeSpec attribute,
      String name, Class<?> type, boolean settable, boolean settableOnCreate) {
    ASSERT.that(attribute.name()).isEqualTo(name);
    ASSERT.that(attribute.type()).isEqualTo(type);
    ASSERT.that(attribute.isUserSettable()).isEqualTo(settable);
    ASSERT.that(attribute.isSettableOnCreate()).isEqualTo(settableOnCreate);
  }
}
