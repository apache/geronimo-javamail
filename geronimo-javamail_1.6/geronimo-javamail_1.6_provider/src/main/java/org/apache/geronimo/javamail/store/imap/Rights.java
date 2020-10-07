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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Represents a set of rights associated with a user to manipulate the 
 * IMAP Store.
 */
public class Rights implements Cloneable {
    
    /**
     * An individual right for IMAP Store manipulation.
     */
    public static final class Right {
        // The set of created stores.  The getInstance() method ensures 
        // that each right is a singleton object. 
        static private Map rights = new HashMap();
        
        /**
         * lookup (mailbox is visible to LIST/LSUB commands)
         */
        public static final Right LOOKUP = getInstance('l'); 
        /**
         * read (SELECT the mailbox, perform CHECK, FETCH, PARTIAL,
         *        SEARCH, COPY from mailbox)
         */
        public static final Right READ = getInstance('r'); 
        /**
         * keep seen/unseen information across sessions (STORE SEEN flag)
         */
        public static final Right KEEP_SEEN = getInstance('s'); 
        /**
         * write (STORE flags other than SEEN and DELETED)
         */
        public static final Right WRITE = getInstance('w'); 
        /**
         * insert (perform APPEND, COPY into mailbox)
         */
        public static final Right INSERT = getInstance('i'); 
        /**
         * post (send mail to submission address for mailbox,
         *        not enforced by IMAP4 itself)
         */
        public static final Right POST = getInstance('p'); 
        /**
         * create (CREATE new sub-mailboxes in any implementation-defined
         *        hierarchy)
         */
        public static final Right CREATE = getInstance('c'); 
        /**
         * delete (STORE DELETED flag, perform EXPUNGE)
         */
        public static final Right DELETE = getInstance('d'); 
        /**
         * administer (perform SETACL)
         */
        public static final Right ADMINISTER = getInstance('a'); 
        
        // the actual right definition 
        String right; 
        
        /**
         * Private constructor for an individual Right.  Used by getInstance().
         * 
         * @param right  The String name of the right (a single character).
         */
        private Right(String right) {
            this.right = right; 
        }
        
        /**
         * Get an instance for a right from the single character right value.  The
         * returned instance will be a singleton for that character value.
         * 
         * @param right  The right character value.
         * 
         * @return A Right instance that's the mapping for the character value.
         */
        public static synchronized Right getInstance(char right) {
            String name = String.valueOf(right); 
            Right instance = (Right)rights.get(name); 
            if (instance == null) {
                instance = new Right(name); 
                rights.put(name, instance); 
            }
            return instance; 
        }
        
        /**
         * Return the string value of the Right.  The string value is the character 
         * used to create the Right with newInstance().
         * 
         * @return The string representation of the Right.
         */
        public String toString() {
            return right; 
        }
    }
    
    /**
     * The set of Rights contained in this instance.  This is a TreeSet so that
     * we can create the string value more consistently.
     */
    private SortedSet rights = new TreeSet(new RightComparator()); 
    
    /**
     * Construct an empty set of Rights.
     */
    public Rights() {
    }
    
    /**
     * Construct a Rights set from a single Right instance.
     * 
     * @param right  The source Right.
     */
    public Rights(Right right) {
        rights.add(right); 
    }
    
    /**
     * Construct a set of rights from an existing Rights set.  This will copy 
     * the rights values.
     * 
     * @param list   The source Rights instance.
     */
    public Rights(Rights list) {
        add(list); 
        Rights[] otherRights = list.getRights(); 
        for (int i = 0; i < otherRights.length; i++) {
            rights.add(otherRights[i]); 
        }
    }
    
    /**
     * Construct a Rights et from a character string.  Each character in the
     * string represents an individual Right.
     * 
     * @param list   The source set of rights.
     */
    public Rights(String list) {
        for (int i = 0; i < list.length(); i++) {
            rights.add(Right.getInstance(list.charAt(i))); 
        }
    }
    
    /**
     * Add a single Right to the set.
     * 
     * @param right  The new Right.  If the Rigtht is already part of the Set, this is a nop.
     */
    public void add(Right right) {
        rights.add(right); 
    }
    
    /**
     * Merge a Rights set with this set.  Duplicates are eliminated.
     * 
     * @param list   The source for the added Rights.
     */
    public void add(Rights list) {
        Rights[] otherRights = list.getRights(); 
        for (int i = 0; i < otherRights.length; i++) {
            rights.add(otherRights[i]); 
        }
    }
    
    /**
     * Clone a set of Rights.
     */
    public Object clone() {
        return new Rights(this); 
    }
    
    /**
     * Test if a Rights set contains a given Right.
     * 
     * @param right  The Right instance to test.
     * 
     * @return true if the Right exists in the Set, false otherwise.
     */
    public boolean contains(Right right) {
        return rights.contains(right); 
    }
    
    /**
     * Test if this Rights set contains all of the Rights contained in another
     * set.
     * 
     * @param list   The source Rights set for the test.
     * 
     * @return true if all of the Rights in the source set exist in the target set.
     */
    public boolean contains(Rights list) {
        return rights.containsAll(list.rights); 
    }
    
    /**
     * Test if two Rights sets are equivalent.
     * 
     * @param list   The source rights set.
     * 
     * @return true if both Rigths sets contain the same Rights values.
     */
    public boolean equals(Rights list) {
        return rights.equals(list.rights); 
    }
    
    /**
     * Get an array of Rights contained in the set.
     * 
     * @return An array of Rights[] values.
     */
    public Rights[] getRights() {
        Rights[] list = new Rights[rights.size()]; 
        return (Rights[])rights.toArray(list); 
    }
    
    /**
     * Compute a hashCode for the Rights set.
     * 
     * @return The computed hashCode.
     */
    public int hashCode() {
        return rights.hashCode(); 
    }
    
    /**
     * Remove a Right from the set.
     * 
     * @param right  The single Right to remove.
     */
    public void remove(Right right) {
        rights.remove(right); 
    }
    
    /**
     * Remove a set of rights from the set.
     * 
     * @param list   The list of rights to be removed.
     */
    public void remove(Rights list) {
        rights.removeAll(list.rights); 
    }
    
    /**
     * Return a string value for the Rights set.  The string value is the 
     * concatenation of the single-character Rights names. 
     * 
     * @return The string representation of this Rights set. 
     */
    public String toString() {
        StringBuffer buff = new StringBuffer(); 
        Iterator i = rights.iterator(); 
        while (i.hasNext()) {
            buff.append(i.next().toString()); 
        }
        return buff.toString(); 
    }
    
    class RightComparator implements Comparator {
        /**
         * Perform a sort comparison to order two Right objects.
         * The sort is performed using the string value. 
         * 
         * @param o1     The left comparator
         * @param o2     The right comparator.
         * 
         * @return 0 if the two items have equal ordering, -1 if the 
         *         left item is lower, 1 if the left item is greater.
         */
        public int compare(Object o1, Object o2) {
            // compare on the string value 
            String left = o1.toString(); 
            return left.compareTo(o2.toString()); 
        }
    }
    
}
