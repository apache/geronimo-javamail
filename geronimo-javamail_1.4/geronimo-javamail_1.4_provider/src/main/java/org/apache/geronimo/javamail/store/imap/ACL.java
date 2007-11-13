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


package org.apache.geronimo.javamail.store.imap;

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
     * Creates and returns a copy of this object.  The precise meaning
     * of "copy" may depend on the class of the object. The general
     * intent is that, for any object <tt>x</tt>, the expression:
     * <blockquote>
     * <pre>
     * x.clone() != x</pre></blockquote>
     * will be true, and that the expression:
     * <blockquote>
     * <pre>
     * x.clone().getClass() == x.getClass()</pre></blockquote>
     * will be <tt>true</tt>, but these are not absolute requirements.
     * While it is typically the case that:
     * <blockquote>
     * <pre>
     * x.clone().equals(x)</pre></blockquote>
     * will be <tt>true</tt>, this is not an absolute requirement.
     * <p>
     * By convention, the returned object should be obtained by calling
     * <tt>super.clone</tt>.  If a class and all of its superclasses (except
     * <tt>Object</tt>) obey this convention, it will be the case that
     * <tt>x.clone().getClass() == x.getClass()</tt>.
     * <p>
     * By convention, the object returned by this method should be independent
     * of this object (which is being cloned).  To achieve this independence,
     * it may be necessary to modify one or more fields of the object returned
     * by <tt>super.clone</tt> before returning it.  Typically, this means
     * copying any mutable objects that comprise the internal "deep structure"
     * of the object being cloned and replacing the references to these
     * objects with references to the copies.  If a class contains only
     * primitive fields or references to immutable objects, then it is usually
     * the case that no fields in the object returned by <tt>super.clone</tt>
     * need to be modified.
     * <p>
     * The method <tt>clone</tt> for class <tt>Object</tt> performs a
     * specific cloning operation. First, if the class of this object does
     * not implement the interface <tt>Cloneable</tt>, then a
     * <tt>CloneNotSupportedException</tt> is thrown. Note that all arrays
     * are considered to implement the interface <tt>Cloneable</tt>.
     * Otherwise, this method creates a new instance of the class of this
     * object and initializes all its fields with exactly the contents of
     * the corresponding fields of this object, as if by assignment; the
     * contents of the fields are not themselves cloned. Thus, this method
     * performs a "shallow copy" of this object, not a "deep copy" operation.
     * <p>
     * The class <tt>Object</tt> does not itself implement the interface
     * <tt>Cloneable</tt>, so calling the <tt>clone</tt> method on an object
     * whose class is <tt>Object</tt> will result in throwing an
     * exception at run time.
     * 
     * @return a clone of this instance.
     * @exception CloneNotSupportedException
     *                   if the object's class does not
     *                   support the <code>Cloneable</code> interface. Subclasses
     *                   that override the <code>clone</code> method can also
     *                   throw this exception to indicate that an instance cannot
     *                   be cloned.
     * @see java.lang.Cloneable
     */
    protected Object clone() throws CloneNotSupportedException {
        return new ACL(name, new Rights(rights)); 
    }
}
