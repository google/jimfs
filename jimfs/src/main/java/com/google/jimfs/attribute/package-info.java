/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Package containing classes used for configuration of file attribute handling.
 *
 * <p>Users can create a subclass of {@link AttributeProvider} to implement handling of a custom
 * file attribute view. In an attribute provider, the {@link Inode} class is used to access and set
 * file attributes.
 *
 * <p>{@link StandardAttributeProviders} provides access to the standard set of
 * {@code AttributeProvider} implementations that JIMFS supports.
 */
@ParametersAreNonnullByDefault
package com.google.jimfs.attribute;

import javax.annotation.ParametersAreNonnullByDefault;