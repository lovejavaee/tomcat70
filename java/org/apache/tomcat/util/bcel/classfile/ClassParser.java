/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.tomcat.util.bcel.classfile;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tomcat.util.bcel.Constants;

/**
 * Wrapper class that parses a given Java .class file. The method <A
 * href ="#parse">parse</A> returns a <A href ="JavaClass.html">
 * JavaClass</A> object on success. When an I/O error or an
 * inconsistency occurs an appropiate exception is propagated back to
 * the caller.
 *
 * The structure and the names comply, except for a few conveniences,
 * exactly with the <A href="ftp://java.sun.com/docs/specs/vmspec.ps">
 * JVM specification 1.0</a>. See this paper for
 * further details about the structure of a bytecode file.
 *
 * @author <A HREF="mailto:m.dahm@gmx.de">M. Dahm</A> 
 */
public final class ClassParser {

    private static final int MAGIC = 0xCAFEBABE;

    private DataInputStream file;
    private String file_name;
    private int class_name_index, superclass_name_index;
    private int access_flags; // Access rights of parsed class
    private int[] interfaces; // Names of implemented interfaces
    private ConstantPool constant_pool; // collection of constants
    private Attribute[] attributes; // attributes defined in the class
    private static final int BUFSIZE = 8192;


    /**
     * Parse class from the given stream.
     *
     * @param file Input stream
     * @param file_name File name
     */
    public ClassParser(InputStream file, String file_name) {
        this.file_name = file_name;
        if (file instanceof DataInputStream) {
            this.file = (DataInputStream) file;
        } else {
            this.file = new DataInputStream(new BufferedInputStream(file, BUFSIZE));
        }
    }


    /**
     * Parse the given Java class file and return an object that represents
     * the contained data, i.e., constants, methods, fields and commands.
     * A <em>ClassFormatException</em> is raised, if the file is not a valid
     * .class file. (This does not include verification of the byte code as it
     * is performed by the java interpreter).
     *
     * @return Class object representing the parsed class file
     * @throws  IOException
     * @throws  ClassFormatException
     */
    public JavaClass parse() throws IOException, ClassFormatException {
        /****************** Read headers ********************************/
        // Check magic tag of class file
        readID();
        // Get compiler version
        readVersion();
        /****************** Read constant pool and related **************/
        // Read constant pool entries
        readConstantPool();
        // Get class information
        readClassInfo();
        // Get interface information, i.e., implemented interfaces
        readInterfaces();
        /****************** Read class fields and methods ***************/
        // Read class fields, i.e., the variables of the class
        readFields();
        // Read class methods, i.e., the functions in the class
        readMethods();
        // Read class attributes
        readAttributes();
        // Check for unknown variables
        //Unknown[] u = Unknown.getUnknownAttributes();
        //for(int i=0; i < u.length; i++)
        //  System.err.println("WARNING: " + u[i]);
        // Everything should have been read now
        //      if(file.available() > 0) {
        //        int bytes = file.available();
        //        byte[] buf = new byte[bytes];
        //        file.read(buf);
        //        if(!(is_zip && (buf.length == 1))) {
        //          System.err.println("WARNING: Trailing garbage at end of " + file_name);
        //          System.err.println(bytes + " extra bytes: " + Utility.toHexString(buf));
        //        }
        //      }

        // Return the information we have gathered in a new object
        return new JavaClass(class_name_index, superclass_name_index,
                access_flags, constant_pool, interfaces, attributes);
    }


    /**
     * Read information about the attributes of the class.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readAttributes() throws IOException, ClassFormatException {
        int attributes_count;
        attributes_count = file.readUnsignedShort();
        attributes = new Attribute[attributes_count];
        for (int i = 0; i < attributes_count; i++) {
            attributes[i] = Attribute.readAttribute(file, constant_pool);
        }
    }


    /**
     * Read information about the class and its super class.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readClassInfo() throws IOException, ClassFormatException {
        access_flags = file.readUnsignedShort();
        /* Interfaces are implicitely abstract, the flag should be set
         * according to the JVM specification.
         */
        if ((access_flags & Constants.ACC_INTERFACE) != 0) {
            access_flags |= Constants.ACC_ABSTRACT;
        }
        if (((access_flags & Constants.ACC_ABSTRACT) != 0)
                && ((access_flags & Constants.ACC_FINAL) != 0)) {
            throw new ClassFormatException("Class " + file_name + " can't be both final and abstract");
        }
        class_name_index = file.readUnsignedShort();
        superclass_name_index = file.readUnsignedShort();
    }


    /**
     * Read constant pool entries.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readConstantPool() throws IOException, ClassFormatException {
        constant_pool = new ConstantPool(file);
    }


    /**
     * Read information about the fields of the class, i.e., its variables.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readFields() throws IOException, ClassFormatException {
        int fields_count = file.readUnsignedShort();
        for (int i = 0; i < fields_count; i++) {
            Utility.swallowFieldOrMethod(file);
        }
    }


    /******************** Private utility methods **********************/
    /**
     * Check whether the header of the file is ok.
     * Of course, this has to be the first action on successive file reads.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readID() throws IOException, ClassFormatException {
        if (file.readInt() != MAGIC) {
            throw new ClassFormatException(file_name + " is not a Java .class file");
        }
    }


    /**
     * Read information about the interfaces implemented by this class.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readInterfaces() throws IOException, ClassFormatException {
        int interfaces_count;
        interfaces_count = file.readUnsignedShort();
        interfaces = new int[interfaces_count];
        for (int i = 0; i < interfaces_count; i++) {
            interfaces[i] = file.readUnsignedShort();
        }
    }


    /**
     * Read information about the methods of the class.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readMethods() throws IOException, ClassFormatException {
        int methods_count;
        methods_count = file.readUnsignedShort();
        for (int i = 0; i < methods_count; i++) {
            Utility.swallowFieldOrMethod(file);
        }
    }


    /**
     * Read major and minor version of compiler which created the file.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readVersion() throws IOException, ClassFormatException {
        // file.readUnsignedShort(); // Unused minor
        // file.readUnsignedShort(); // Unused major
        Utility.skipFully(file, 4);
    }
}
