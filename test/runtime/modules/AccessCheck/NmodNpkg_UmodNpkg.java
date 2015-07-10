/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary class p1.c1 defined in m1 tries to access p2.c2 defined in unnamed module.
 * @library /testlibrary /../../test/lib
 * @compile p2/c2.java
 * @compile p1/c1.java
 * @build NmodNpkg_UmodNpkg
 * @run main/othervm -Xbootclasspath/a:. NmodNpkg_UmodNpkg
 */

import static jdk.test.lib.Asserts.*;

import java.lang.module.Configuration;
import java.lang.module.Layer;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReference;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Module;
import java.util.HashMap;
import java.util.Map;

//
// ClassLoader1 --> defines m1 --> packages p1
//                  package p1 in m1 is exported unqualifiedly
//
// class p1.c1 defined in m1 tries to access p2.c2 defined in
// in unnamed module.
//
// Three access attempts occur in this test:
//   1. The first access is not allowed because a strict module
//      cannot read an unnamed module.
//   2. In this scenario a strict module establishes readability
//      to the particular unnamed module it is trying to access.
//      Access is allowed.
//   3. Module m1 in the test_looseModuleLayer() method
//      is transitioned to a loose module, access
//      to all unnamed modules is allowed.
//
public class NmodNpkg_UmodNpkg {

 // Create Layers over the boot layer to test different
 // accessing scenarios of a named module to an unnamed module.

 // Module m1 is a strict module and has not established
 // readability to an unnamed module that p2.c2 is defined in.
 public void test_strictModuleLayer() throws Throwable {

     // Define module:     m1
     // Can read:          java.base
     // Packages:          p1
     // Packages exported: p1 is exported unqualifiedly
     ModuleDescriptor descriptor_m1 =
             new ModuleDescriptor.Builder("m1")
                     .requires("java.base")
                     .exports("p1")
                     .build();

     // Set up a ModuleFinder containing all modules for this layer.
     ModuleFinder finder = ModuleLibrary.of(descriptor_m1);

     // Resolves a module named "m1" that results in a configuration.  It
     // then augments that configuration with additional modules (and edges) induced
     // by service-use relationships.
     Configuration cf = Configuration.resolve(finder,
                                              Layer.boot(),
                                              ModuleFinder.empty(),
                                              "m1");

     // map module m1 to class loader.
     // class c2 will be loaded in an unnamed module/loader.
     MySameClassLoader loader = new MySameClassLoader();
     Map<String, ClassLoader> map = new HashMap<>();
     map.put("m1", loader);

     // Create Layer that contains m1
     Layer layer = Layer.create(cf, map::get);

     assertTrue(layer.findLoader("m1") == loader);
     assertTrue(layer.findLoader("java.base") == null);

     // now use the same loader to load class p1.c1
     Class p1_c1_class = loader.loadClass("p1.c1");

     // Attempt access
     try {
         p1_c1_class.newInstance();
         throw new RuntimeException("Test Failed, strict module m1 should not be able to access " +
                                    "public type p2.c2 defined in unnamed module");
     } catch (IllegalAccessError e) {
     }
 }

 // Module m1 is a strict module and has established
 // readability to an unnamed module that p2.c2 is defined in.
 public void test_strictModuleUnnamedReadableLayer() throws Throwable {

     // Define module:     m1
     // Can read:          java.base
     // Packages:          p1
     // Packages exported: p1 is exported unqualifiedly
     ModuleDescriptor descriptor_m1 =
             new ModuleDescriptor.Builder("m1")
                     .requires("java.base")
                     .exports("p1")
                     .build();

     // Set up a ModuleFinder containing all modules for this layer.
     ModuleFinder finder = ModuleLibrary.of(descriptor_m1);

     // Resolves a module named "m1" that results in a configuration.  It
     // then augments that configuration with additional modules (and edges) induced
     // by service-use relationships.
     Configuration cf = Configuration.resolve(finder,
                                              Layer.boot(),
                                              ModuleFinder.empty(),
                                              "m1");

     MySameClassLoader loader = new MySameClassLoader();
     // map module m1 to class loader.
     // class c2 will be loaded in an unnamed module/loader.
     Map<String, ClassLoader> map = new HashMap<>();
     map.put("m1", loader);

     // Create Layer that contains m1
     Layer layer = Layer.create(cf, map::get);

     assertTrue(layer.findLoader("m1") == loader);
     assertTrue(layer.findLoader("java.base") == null);

     // now use the same loader to load class p1.c1
     Class p1_c1_class = loader.loadClass("p1.c1");

     // Establish readability between module m1 and the
     // unnamed module of loader.
     Module unnamed_module = loader.getUnnamedModule();
     Module m1 = p1_c1_class.getModule();
     m1.addReads(unnamed_module);

     // Attempt access
     try {
        p1_c1_class.newInstance();
     } catch (IllegalAccessError e) {
         throw new RuntimeException("Test Failed, module m1 has established readability to " +
                                    "p2/c2 loader's unnamed module, access should be allowed: " + e.getMessage());
     }
 }

 // Module m1 is a loose module and thus can read all unnamed modules.
 public void test_looseModuleLayer() throws Throwable {

     // Define module:     m1
     // Can read:          java.base
     // Packages:          p1
     // Packages exported: p1 is exported unqualifiedly
     ModuleDescriptor descriptor_m1 =
             new ModuleDescriptor.Builder("m1")
                     .requires("java.base")
                     .exports("p1")
                     .build();

     // Set up a ModuleFinder containing all modules for this layer.
     ModuleFinder finder = ModuleLibrary.of(descriptor_m1);

     // Resolves a module named "m1" that results in a configuration.  It
     // then augments that configuration with additional modules (and edges) induced
     // by service-use relationships.
     Configuration cf = Configuration.resolve(finder,
                                              Layer.boot(),
                                              ModuleFinder.empty(),
                                              "m1");

     MySameClassLoader loader = new MySameClassLoader();
     // map module m1 to class loader.
     // class c2 will be loaded in an unnamed module/loader.
     Map<String, ClassLoader> map = new HashMap<>();
     map.put("m1", loader);

     // Create Layer that contains m1
     Layer layer = Layer.create(cf, map::get);

     assertTrue(layer.findLoader("m1") == loader);
     assertTrue(layer.findLoader("java.base") == null);

     // now use the same loader to load class p1.c1
     Class p1_c1_class = loader.loadClass("p1.c1");

     // Transition module "m1" to be a loose module
     Module m1 = p1_c1_class.getModule();
     m1.addReads(null);

     // Attempt access
     try {
        p1_c1_class.newInstance();
     } catch (IllegalAccessError e) {
         throw new RuntimeException("Test Failed, loose module m1 should be able to acccess public type " +
                                    "p2.c2 defined in unnamed module: " + e.getMessage());
     }
 }

 public static void main(String args[]) throws Throwable {
   NmodNpkg_UmodNpkg test = new NmodNpkg_UmodNpkg();
   test.test_strictModuleLayer(); // access denied
   test.test_strictModuleUnnamedReadableLayer(); // access allowed
   test.test_looseModuleLayer(); // access allowed
 }
}
