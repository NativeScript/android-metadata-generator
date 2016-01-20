package com.telerik.metadata;

import java.util.ArrayList;
import java.util.Comparator;

import org.apache.bcel.classfile.JavaClass;

public class ClassRepo {
	private ClassRepo() {
	}

	private static ArrayList<JarFile> jars = new ArrayList<JarFile>();

	public static void cacheJarFile(JarFile jar) {
		for (String className : jar.classes.keySet()) {
			for (JarFile cachedJar : jars) {
				JavaClass clazz = cachedJar.classes.get(className);
				if (clazz != null) {
					String errMsg = "Class " + className + " conflict: "
							+ jar.getPath() + " and " + cachedJar.getPath();
					throw new IllegalArgumentException(errMsg);
				}
			}
		}
		jars.add(jar);
	}

	public static JavaClass findClass(String className) {
		JavaClass clazz = null;
		for (JarFile jar : jars) {
			clazz = jar.classes.get(className);
			if (clazz != null) {
				break;
			}
		}
		return clazz;
	}

	public static String[] getClassNames() {
		ArrayList<String> names = new ArrayList<String>();
		for (JarFile jar : jars) {
			for (String className : jar.classes.keySet()) {
				names.add(className);
			}
		}
		String[] arrClassNames = names.toArray(new String[names.size()]);
		return arrClassNames;
	}
}
