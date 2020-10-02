/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.geronimo.javamail.util;
 
import java.util.Properties;

import javax.mail.Session;

/**
 * Interface for providing access to protocol specific properties to 
 * utility classes. 
 */
public class ProtocolProperties {
    // the protocol we're working with. 
    protected String protocol; 
    // a preconstructed prefix string to reduce concatenation operations.  
    protected String protocolPrefix; 
    // the Session that's the source of all of the properties 
    protected Session session; 
    // the sslConnection property.  This indicates this protocol is to use SSL for 
    // all communications with the server. 
    protected boolean sslConnection;
    // the default port property.  The default port differs with the protocol 
    // and the sslConnection property. 
    protected int defaultPort; 
    
    
    public ProtocolProperties(Session session, String protocol, boolean sslConnection, int defaultPort) {
        this.session = session; 
        this.protocol = protocol; 
        this.sslConnection = sslConnection; 
        this.defaultPort = defaultPort; 
        // this helps avoid a lot of concatenates when retrieving properties. 
        protocolPrefix = "mail." + protocol + ".";
    }
    
    
    /**
     * Retrieve the Session associated with this property bundle instance.
     * 
     * @return A Session object that's the source of the accessed properties. 
     */
    public Session getSession() {
        return session; 
    }
    
    
    /**
     * Retrieve the name of the protocol used to access properties.
     * 
     * @return The protocol String name. 
     */
    public String getProtocol() {
        return protocol; 
    }
    
    
    /**
     * Retrieve the SSL Connection flag for this protocol; 
     * 
     * @return true if an SSL connection is required, false otherwise. 
     */
    public boolean getSSLConnection() {
        return sslConnection; 
    }
    
    
    /**
     * Return the default port to use with this connection.
     * 
     * @return The default port value. 
     */
    public int getDefaultPort() {
        return defaultPort; 
    }
    
    
    /**
     * Get a property associated with this mail protocol.
     *
     * @param name   The name of the property.
     *
     * @return The property value (returns null if the property has not been set).
     */
    public String getProperty(String name) {
        // the name we're given is the least qualified part of the name.  
        // We construct the full property name
        // using the protocol
        String fullName = protocolPrefix + name;
        return session.getProperty(fullName);
    }
    
    /**
     * Get a property (as object) associated with this mail protocol.
     *
     * @param name   The name of the property.
     *
     * @return The property value (returns null if the property has not been set).
     */
    public Object getPropertyAsObject(String name) {
        // the name we're given is the least qualified part of the name.  
        // We construct the full property name
        // using the protocol
        String fullName = protocolPrefix + name;
        return session.getProperties().get(fullName);
    }

    /**
     * Get a property associated with this mail session.  Returns
     * the provided default if it doesn't exist.
     *
     * @param name   The name of the property.
     * @param defaultValue
     *               The default value to return if the property doesn't exist.
     *
     * @return The property value (returns defaultValue if the property has not been set).
     */
    public String getProperty(String name, String defaultValue) {
        // the name we're given is the least qualified part of the name.  
        // We construct the full property name
        // using the protocol
        String fullName = protocolPrefix + name;
        String value = session.getProperty(fullName);
        if (value == null) {
            value = defaultValue; 
        }
        return value; 
    }


    /**
     * Get a property associated with this mail session as an integer value.  Returns
     * the default value if the property doesn't exist or it doesn't have a valid int value.
     *
     * @param name   The name of the property.
     * @param defaultValue
     *               The default value to return if the property doesn't exist.
     *
     * @return The property value converted to an int.
     */
    public int getIntProperty(String name, int defaultValue)
    {
        // retrieve the property 
        String value = getProperty(name); 
        // return the default value if not set. 
        if (value == null) {
            return defaultValue; 
        }
        return Integer.parseInt(value); 
    }
    

    /**
     * Get a property associated with this mail session as an boolean value.  Returns
     * the default value if the property doesn't exist or it doesn't have a valid int value.
     *
     * @param name   The name of the property.
     * @param defaultValue
     *               The default value to return if the property doesn't exist.
     *
     * @return The property value converted to a boolean
     */
    public boolean getBooleanProperty(String name, boolean defaultValue)
    {
        // retrieve the property 
        String value = getProperty(name); 
        // return the default value if not set. 
        if (value == null) {
            return defaultValue; 
        }
        // just do a single test for true. 
        if ("true".equals(value)) {
            return true; 
        }
        // return false for anything other than true
        return false; 
    }
    
    
    /**
     * Get a property associated with this mail session.  Session 
     * properties all begin with "mail."
     *
     * @param name   The name of the property.
     *
     * @return The property value (returns null if the property has not been set).
     */
    public String getSessionProperty(String name) {
        // the name we're given is the least qualified part of the name.  
        // We construct the full property name
        // using the protocol
        String fullName = "mail." + name;
        return session.getProperty(fullName);
    }

    /**
     * Get a property associated with this mail session.  Returns
     * the provided default if it doesn't exist.
     *
     * @param name   The name of the property.
     * @param defaultValue
     *               The default value to return if the property doesn't exist.
     *
     * @return The property value (returns defaultValue if the property has not been set).
     */
    public String getSessionProperty(String name, String defaultValue) {
        // the name we're given is the least qualified part of the name.  
        // We construct the full property name
        // using the protocol
        String fullName = "mail." + name;
        String value = session.getProperty(fullName);
        if (value == null) {
            value = defaultValue; 
        }
        return value; 
    }


    /**
     * Get a property associated with this mail session as an integer value.  Returns
     * the default value if the property doesn't exist or it doesn't have a valid int value.
     *
     * @param name   The name of the property.
     * @param defaultValue
     *               The default value to return if the property doesn't exist.
     *
     * @return The property value converted to an int.
     */
    public int getIntSessionProperty(String name, int defaultValue)
    {
        // retrieve the property 
        String value = getSessionProperty(name); 
        // return the default value if not set. 
        if (value == null) {
            return defaultValue; 
        }
        return Integer.parseInt(value); 
    }
    

    /**
     * Get a property associated with this mail session as an boolean value.  Returns
     * the default value if the property doesn't exist or it doesn't have a valid int value.
     *
     * @param name   The name of the property.
     * @param defaultValue
     *               The default value to return if the property doesn't exist.
     *
     * @return The property value converted to a boolean
     */
    public boolean getBooleanSessionProperty(String name, boolean defaultValue)
    {
        // retrieve the property 
        String value = getSessionProperty(name); 
        // return the default value if not set. 
        if (value == null) {
            return defaultValue; 
        }
        // just do a single test for true. 
        if ("true".equals(value)) {
            return true; 
        }
        // return false for anything other than true
        return false; 
    }
    
    /**
     * Get the complete set of properties associated with this Session.
     * 
     * @return The Session properties bundle. 
     */
    public Properties getProperties() {
        return session.getProperties(); 
    }
    
}
