/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openjpa.enhance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import mx.kenzie.overlord.Overlord;
import org.apache.openjpa.lib.util.JavaVendors;


/**
 * Factory for obtaining an {@link Instrumentation} instance.
 *
 * @author Marc Prud'hommeaux
 * @since 1.0.0
 */
public class InstrumentationFactory {
    private static Instrumentation _inst;
    private static boolean _dynamicallyInstall = true;
    private static final String _name = InstrumentationFactory.class.getName();

    /**
     * This method is not synchronized because when the agent is loaded from
     * getInstrumentation() that method will cause agentmain(..) to be called.
     * Synchronizing this method would cause a deadlock.
     *
     * @param inst The instrumentation instance to be used by this factory.
     */
    public static void setInstrumentation(Instrumentation inst) {
        _inst = inst;
    }

    /**
     * Configures whether or not this instance should attempt to dynamically
     * install an agent in the VM. Defaults to <code>true</code>.
     */
    public static synchronized void setDynamicallyInstallAgent(boolean val) {
        _dynamicallyInstall = val;
    }

    /**
     * @return null if Instrumentation can not be obtained, or if any
     * Exceptions are encountered.
     */
    public static synchronized Instrumentation getInstrumentation() {
        if ( _inst != null || !_dynamicallyInstall)
            return _inst;

        // end run()
        // Dynamic agent enhancement should only occur when the OpenJPA library is
        // loaded using the system class loader.  Otherwise, the OpenJPA
        // library may get loaded by separate, disjunct loaders, leading to linkage issues.
        try {
            if (!InstrumentationFactory.class.getClassLoader().equals(
                    ClassLoader.getSystemClassLoader())) {
                return null;
            }
        } catch (Throwable t) {
            return null;
        }

        try {
            Class<?> jdkVM = Class.forName("jdk.internal.misc.VM");
            Overlord.breakEncapsulation(InstrumentationFactory.class, jdkVM, true);
            Overlord.allowAccess(InstrumentationFactory.class, jdkVM, true);
            VarHandle props = MethodHandles.privateLookupIn(jdkVM, MethodHandles.lookup())
                    .findStaticVarHandle(jdkVM, "savedProps", Map.class);
            //noinspection unchecked - known from VM source code
            ((Map<String, String>) props.get()).put("jdk.attach.allowAttachSelf", "true");
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }

        JavaVendors vendor = JavaVendors.getCurrentVendor();
        File toolsJar = null;
        // When running on IBM, the attach api classes are packaged in vm.jar which is a part
        // of the default vm classpath.
        if (!vendor.isIBM()) {
            // If we can't find the tools.jar and we're not on IBM we can't load the agent.
            // EXCEPT SOMETIMES WE CAN :) for example temurin has com.sun.tools.attach included
            toolsJar = findToolsJar();
        }

        Class<?> vmClass = loadVMClass(toolsJar, vendor);


        if (vmClass == null) {
            return null;
        }
        String agentPath = getAgentJar();
        if (agentPath == null) {
            return null;
        }
        loadAgent(agentPath, vmClass);
        // If the load(...) agent call was successful, this variable will no
        // longer be null.
        return _inst;
    }//end getInstrumentation()

    /**
     *  The method that is called when a jar is added as an agent at runtime.
     *  All this method does is store the {@link Instrumentation} for
     *  later use.
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        InstrumentationFactory.setInstrumentation(inst);
    }

    /**
     * Create a new jar file for the sole purpose of specifying an Agent-Class
     * to load into the JVM.
     *
     * @return absolute path to the new jar file.
     */
    private static String createAgentJar() throws IOException {
        File file =
                File.createTempFile(InstrumentationFactory.class.getName(), ".jar");
        file.deleteOnExit();

        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(file));
        zout.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(zout));

        writer
                .println("Agent-Class: " + InstrumentationFactory.class.getName());
        writer.println("Can-Redefine-Classes: true");
        // IBM doesn't support retransform
        writer.println("Can-Retransform-Classes: " + (!JavaVendors.getCurrentVendor().isIBM()));

        writer.close();

        return file.getAbsolutePath();
    }

    /**
     * This private worker method attempts to find [java_home]/lib/tools.jar.
     * Note: The tools.jar is a part of the SDK, it is not present in the JRE.
     *
     * @return If tools.jar can be found, a File representing tools.jar. <BR>
     *         If tools.jar cannot be found, null.
     */
    private static File findToolsJar() {
        String javaHome = System.getProperty("java.home");
        File javaHomeFile = new File(javaHome);

        File toolsJarFile = new File(javaHomeFile, "lib" + File.separator + "tools.jar");
        if (!toolsJarFile.exists()) {
            // If we're on an IBM SDK, then remove /jre off of java.home and try again.
            if (javaHomeFile.getAbsolutePath().endsWith(File.separator + "jre")) {
                javaHomeFile = javaHomeFile.getParentFile();
                toolsJarFile = new File(javaHomeFile, "lib" + File.separator + "tools.jar");
            } else if (System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("mac")) {
                // If we're on a Mac, then change the search path to use ../Classes/classes.jar.
                if (javaHomeFile.getAbsolutePath().endsWith(File.separator + "Home")) {
                    javaHomeFile = javaHomeFile.getParentFile();
                    toolsJarFile = new File(javaHomeFile, "Classes" + File.separator + "classes.jar");
                }
            }
        }

        if (!toolsJarFile.exists()) {
            return null;
        } else {
            return toolsJarFile;
        }
    }

    /**
     * This private worker method will return a fully qualified path to a jar
     * that has this class defined as an Agent-Class in it's
     * META-INF/manifest.mf file. Under normal circumstances the path should
     * point to the OpenJPA jar. If running in a development environment a
     * temporary jar file will be created.
     *
     * @return absolute path to the agent jar or null if anything unexpected
     * happens.
     */
    private static String getAgentJar() {
        File agentJarFile = null;
        // Find the name of the File that this class was loaded from. That
        // jar *should* be the same location as our agent.
        CodeSource cs =
                InstrumentationFactory.class.getProtectionDomain().getCodeSource();
        if (cs != null) {
            URL loc = cs.getLocation();
            if(loc!=null){
                agentJarFile = new File(loc.getFile());
            }
        }

        // Determine whether the File that this class was loaded from has this
        // class defined as the Agent-Class.
        boolean createJar = false;
        if (cs == null || agentJarFile == null
                || agentJarFile.isDirectory()) {
            createJar = true;
        }else if(!validateAgentJarManifest(agentJarFile, _name)){
            // We have an agentJarFile, but this class isn't the Agent-Class.
            createJar=true;
        }

        String agentJar;
        if (createJar) {
            // This can happen when running in eclipse as an OpenJPA
            // developer or for some reason the CodeSource is null. We
            // should log a warning here because this will create a jar
            // in your temp directory that doesn't always get cleaned up.
            try {
                agentJar = createAgentJar();
            } catch (IOException ioe) {
                agentJar = null;
            }
        } else {
            agentJar = agentJarFile.getAbsolutePath();
        }

        return agentJar;
    }//end getAgentJar

    /**
     * Attach and load an agent class.
     *
     * @param agentJar absolute path to the agent jar.
     * @param vmClass VirtualMachine.class from tools.jar.
     */
    private static void loadAgent(String agentJar, Class<?> vmClass) {
        try {
            // first obtain the PID of the currently-running process
            // ### this relies on the undocumented convention of the
            // RuntimeMXBean's
            // ### name starting with the PID, but there appears to be no other
            // ### way to obtain the current process' id, which we need for
            // ### the attach process
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            String pid = runtime.getName();
            if (pid.contains("@"))
                pid = pid.substring(0, pid.indexOf("@"));

            // JDK1.6: now attach to the current VM so we can deploy a new agent
            // ### this is a Sun JVM specific feature; other JVMs may offer
            // ### this feature, but in an implementation-dependent way
            Object vm =
                    vmClass.getMethod("attach", String.class)
                            .invoke(null, pid);
            // now deploy the actual agent, which will wind up calling
            // agentmain()
            vmClass.getMethod("loadAgent", String.class)
                    .invoke(vm, agentJar);
            vmClass.getMethod("detach").invoke(vm);
        } catch (Throwable ignored) {
        }
    }

    /**
     * If <b>ibm</b> is false, this private method will create a new URLClassLoader and attempt to load the
     * com.sun.tools.attach.VirtualMachine class from the provided toolsJar file.
     *
     * <p>
     * If <b>ibm</b> is true, this private method will ignore the toolsJar parameter and load the
     * com.ibm.tools.attach.VirtualMachine class.
     *
     *
     * @return The AttachAPI VirtualMachine class <br>
     *         or null if something unexpected happened.
     */
    private static Class<?> loadVMClass(File toolsJar, JavaVendors vendor) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            String cls = vendor.getVirtualMachineClassName();
            URL[] urls = new URL[1];
            if (toolsJar != null)
                urls[0] = toolsJar.toURI().toURL();

            if (!vendor.isIBM() && toolsJar != null) {
                loader = new URLClassLoader(urls, loader);
            }
            return loader.loadClass(cls);
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * This private worker method will validate that the provided agentClassName
     * is defined as the Agent-Class in the manifest file from the provided jar.
     *
     * @param agentJarFile
     *            non-null agent jar file.
     *            non-null logger.
     * @param agentClassName
     *            the non-null agent class name.
     * @return True if the provided agentClassName is defined as the Agent-Class
     *         in the manifest from the provided agentJarFile. False otherwise.
     */
    private static boolean validateAgentJarManifest(File agentJarFile, String agentClassName) {
        try (JarFile jar = new JarFile(agentJarFile)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return false;
            }
            Attributes attributes = manifest.getMainAttributes();
            String ac = attributes.getValue("Agent-Class");
            if (ac != null && ac.equals(agentClassName)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }// end validateAgentJarManifest
}
