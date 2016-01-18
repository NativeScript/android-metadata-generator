package com.telerik.metadata;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;

public class JarFile
{
	private final String path;
	
	private JarFile(String path) {
		this.path = path;
	}
	
	public String getPath() {
		return path;
	}
	
	public final Map<String, JavaClass> classes = new HashMap<String, JavaClass>();
	
	private static final String CLASS_EXT = ".class";
	
	public static JarFile readJar(String path) throws ClassFormatException, IOException
	{
		JarFile jar;
		JarInputStream jis = null;
		try {
			jis = new JarInputStream(new FileInputStream(path));
			
			jar = new JarFile(path);
			
			for (ZipEntry ze = jis.getNextEntry(); ze != null; ze = jis.getNextEntry())
			{
				String name = ze.getName();
				if (name.endsWith(CLASS_EXT))
				{
					name = name.substring(0, name.length() - CLASS_EXT.length()).replace('/', '.');
					ClassParser cp = new ClassParser(jis, name);
					JavaClass clazz = cp.parse();
					jar.classes.put(name, clazz);
				}
			}
		} finally {
			if (jis != null)
			{
				jis.close();
			}
		}
		return jar;
	}

}
