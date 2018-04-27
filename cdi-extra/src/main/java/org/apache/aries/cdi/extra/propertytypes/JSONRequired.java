/*
 * Copyright (c) OSGi Alliance (2017, 2018). All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.aries.cdi.extra.propertytypes;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;
import static javax.ws.rs.core.MediaType.*;
import static org.osgi.annotation.bundle.Requirement.Resolution.*;
import static org.osgi.namespace.service.ServiceNamespace.*;
import static org.osgi.resource.Namespace.*;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.osgi.annotation.bundle.Requirement;
import org.osgi.service.cdi.annotations.BeanPropertyType;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;
import org.osgi.service.jaxrs.whiteboard.annotations.RequireJaxrsWhiteboard;

/**
 * Component Property Type for requiring JSON media type support using the
 * {@link JaxrsWhiteboardConstants#JAX_RS_MEDIA_TYPE} service property.
 * <p>
 * This annotation can be used on a JAX-RS resource to declare require that JSON
 * support is available before the resource becomes active. It also adds an
 * optional {@link Requirement} for a service providing this media type to aid
 * with provisioning.
 */
@BeanPropertyType
@RequireJaxrsWhiteboard
@Requirement(
	namespace = SERVICE_NAMESPACE, //
	filter = JSONRequired.FILTER, //
	resolution = OPTIONAL, //
	effective = EFFECTIVE_ACTIVE
)
@Retention(RUNTIME)
@Target({FIELD, METHOD, TYPE})
public @interface JSONRequired {
	/**
	 * A filter requiring an <code>osgi.jaxrs.media.type</code> of
	 * <code>application/json</code>
	 */
	public static final String FILTER = "(osgi.jaxrs.media.type="
			+ APPLICATION_JSON + ")";

	/**
	 * Provides an extension selection filter for an extension supporting the
	 * JSON media type
	 *
	 * @return A filter requiring an <code>osgi.jaxrs.media.type</code> of
	 *         <code>application/json</code>
	 */
	String osgi_jaxrs_extension_select() default FILTER;
}
