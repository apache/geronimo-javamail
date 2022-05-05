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


package org.apache.geronimo.mail.store.imap;

/**
 * A named access control list for IMAP resources.  
 */
public class ACL implements Cloneable {
    /**
     * The name of the resource this ACL applies to.
     */
    private String name; 
    /**
     * The rights associated with this resource.
     */
    private Rights rights;
    
    /**
     * Create an ACL for a resource.  The ACL will have an empty Rights set.
     * 
     * @param name   The name of the resource.
     */
    public ACL(String name) {
        this.name = name; 
        this.rights = new Rights(); 
    }
    
    /**
     * Create a named ACL instance with an initial Rights set.
     * 
     * @param name   The name of the resouce this ACL applies to.
     * @param rights The Rights associated with this resource.
     */
    public ACL(String name, Rights rights) {
        this.name = name; 
        this.rights = rights;  
    }
    
    /**
     * Get the ACL name.
     * 
     * @return The string name of the ACL.
     */
    public String getName() {
        return name; 
    }
    
    /**
     * Get the Rights associated with this ACL.
     * 
     * @return The Rights set supported for this resource.
     */
    public Rights getRights() {
        return rights; 
    }
    
    /**
     * Set a new set of Rights for this ACL instance.
     * 
     * @param rights The new Rights set.
     */
    public void setRights(Rights rights) {
        this.rights = rights;         
    }
    
    
    /**
     * Creates and returns a copy of this object. 
     * 
     * @return A cloned copy of this object.  This is a deep 
     *         copy, given that a new Rights set is also created.
     * @exception CloneNotSupportedException
     */
    protected Object clone() throws CloneNotSupportedException {
        return new ACL(name, new Rights(rights)); 
    }
}
