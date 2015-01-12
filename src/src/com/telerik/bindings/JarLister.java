package com.telerik.bindings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.telerik.bindings.TreeNode.FieldInfo;
import com.telerik.bindings.TreeNode.MethodInfo;

public class JarLister
{
	private static class MethodNameComparator implements Comparator<Method>
	{
		@Override
		public int compare(Method o1, Method o2) {
			return o1.getName().compareTo(o2.getName());
		}
	}
	
	private static MethodNameComparator methodNameComparator = new MethodNameComparator();
	

	private static void ensureDirectories(String outDir, String[] parts)
	{
		File curDir = new File(outDir);

		for (int i = 0; i < parts.length; i++)
		{
			String dir = parts[i];

			String nextDir = curDir.getPath() + "/" + dir;

			File next = new File(nextDir);

			if (!next.exists())
			{
				next.mkdir();
			}
			curDir = next;
		}
	}

	private static ArrayList<TreeNode> getMethodSignature(TreeNode root, Class<?> retType, Class<?>[] params) throws Exception
	{
		ArrayList<TreeNode> sig = new ArrayList<TreeNode>();
		
		
		boolean isVoid = retType.getName().equals("void");
		
		TreeNode node = null;
		
		if (!isVoid)
		{
			node = getOrCreateNode(root, retType);
		}
		sig.add(node);
		
		for (Class<?> param : params)
		{
			node = getOrCreateNode(root, param);
			sig.add(node);
		}		
		
		return sig;
	}
	
	private static TreeNode getOrCreateNode(TreeNode root, Class<?> clazz) throws Exception
	{
		Class<?> c = clazz;
		
		if (c.isPrimitive())
		{
			return TreeNode.getPrimitive(c); 
		}
		
		TreeNode node = root;
		
		boolean isArray = false;
		
		while (c.isArray())
		{
			isArray = true;
			TreeNode child = node.getChild("[");
			if (child == null)
			{
				child = node.createChild("[");
				child.nodeType = TreeNode.Array;
				child.offsetValue = 1;
			}
			c = c.getComponentType();
			node = child;
		}
		
		String name;
		if (c.isPrimitive())
		{
			if (c.equals(byte.class))
			{
				name = "B";
			}
			else if (c.equals(short.class))
			{
				name = "C";
			}
			else if (c.equals(int.class))
			{
				name = "I";
			}
			else if (c.equals(long.class))
			{
				name = "J";
			}
			else if (c.equals(float.class))
			{
				name = "F";
			}
			else if (c.equals(double.class))
			{
				name = "D";
			}
			else if (c.equals(boolean.class))
			{
				name = "Z";
			}
			else if (c.equals(char.class))
			{
				name = "C";
			}
			else
			{
				throw new Exception("unknown primitive type=" + c.getName());
			}
		}
		else
		{
			name = c.getSimpleName();
		}
		
		if (isArray)
		{
			name = c.getCanonicalName();
			TreeNode child = node.getChild(name);
			if (child == null)
			{
				child = node.createChild(name);
				if (c.isPrimitive())
				{
					TreeNode tmp = TreeNode.getPrimitive(c);
					child.nodeType = tmp.nodeType;
				}
				else
				{
					child.nodeType = c.isInterface() ? TreeNode.Interface : TreeNode.Class;
					int classModifilers = c.getModifiers();
					if (Modifier.isStatic(classModifilers))
					{
						child.nodeType |= TreeNode.Static;	
					}
				}
				child.arrayElement = getOrCreateNode(root, c);
			}			
			node = child;
			
			return node;
		}
		
		String[] packages = null;
		if (c.isPrimitive())
		{
			packages = new String[0];
		}
		else
		{
			packages = c.getPackage().getName().split("\\.");
		}
		
		for (String p: packages)
		{
			TreeNode child = node.getChild(p);
			if (child == null)
			{
				child = node.createChild(p);
				node.nodeType = TreeNode.Package;
			}			
			node = child;
		}
		
		Class<?> outer = c.getEnclosingClass();
		ArrayList<Class<?>> outerClasses = new ArrayList<Class<?>>(); 
		while (outer != null)
		{
			outerClasses.add(outer);
			outer = outer.getEnclosingClass();
		}
		
		if (outerClasses.size() > 0)
		{
			for (int i = outerClasses.size() - 1; i >= 0; i--)
			{
				outer = outerClasses.get(i);
				String outerClassname = outer.getSimpleName();
				TreeNode child = node.getChild(outerClassname);
				if (child == null)
				{
					child = node.createChild(outerClassname);
					child.nodeType = outer.isInterface() ? TreeNode.Interface : TreeNode.Class;
					int outerModifilers = outer.getModifiers();
					if (Modifier.isStatic(outerModifilers))
					{
						child.nodeType |= TreeNode.Static;	
					}
				}
				node = child;
			}
		}
		
		TreeNode child = node.getChild(name);
		if (child == null)
		{
			child = node.createChild(name);
			if (c.isPrimitive())
			{
				TreeNode tmp = TreeNode.getPrimitive(c);
				child.nodeType = tmp.nodeType;
			}
			else
			{
				child.nodeType = c.isInterface() ? TreeNode.Interface : TreeNode.Class;
				int classModifilers = c.getModifiers();
				if (Modifier.isStatic(classModifilers))
				{
					child.nodeType |= TreeNode.Static;	
				}
			}
		}			
		node = child;
		
		Class<?> baseClass = clazz.getSuperclass();
		if (baseClass != null)
			node.baseClassNode = getOrCreateNode(root, baseClass);
		
		return node;
	}

	private static void generateJavaBindings(Class<?> clazz, String outDir) throws Exception
	{

		if (clazz.isSynthetic())
		{
			return;
		}

		if (clazz.getEnclosingClass() != null)
		{
			return;
		}

		int clazzModifiers = clazz.getModifiers();

		if (!Modifier.isPublic(clazzModifiers))
		{
			return;
		}
		
		boolean isFinalClass = Modifier.isFinal(clazzModifiers);

		if (isFinalClass)
		{
			boolean hasNestedInterfaces = checkForPublicNestedInterfaces(clazz);
			boolean hasNestedClasses = checkForPublicNestedStaticClasses(clazz);

			if (!hasNestedInterfaces && !hasNestedClasses)
				return;
		}

		if (Modifier.isStatic(clazzModifiers))
		{
			throw new Exception("this should NEVER happen");
		}

		if (clazz.getCanonicalName().startsWith("java.nio."))
		{
			return;
		}

		String baseDir = outDir;

		Package classPackage = clazz.getPackage();

		String[] parts = classPackage.getName().split("\\.");

		ensureDirectories(baseDir, parts);

		String path = classPackage.getName().replace('.', '/') + "/";

		String packagePrefix = "com.tns.";

		FileOutputStream fos = null;
		OutputStreamWriter out = null;

		boolean hasPublicCtors = false;

		for (Constructor<?> c : clazz.getConstructors())
		{
			if (c.isSynthetic())
			{
				continue;
			}

			int modifiers = c.getModifiers();

			boolean isPublic = Modifier.isPublic(modifiers);
			boolean isStatic = Modifier.isStatic(modifiers);

			if (isPublic && !isStatic)
			{
				hasPublicCtors = checkForPublicSignatureTypes(c.getParameterTypes());
				if (hasPublicCtors)
				{
					break;
				}
			}
		}

		if (!clazz.isInterface() && !hasPublicCtors && !isFinalClass)
		{
			return;
		}

		fos = new FileOutputStream(baseDir + path + clazz.getSimpleName() + ".java");
		out = new OutputStreamWriter(fos, "UTF-8");
		//

		out.write("package " + packagePrefix + classPackage.getName() + ";\n\n");

		generateJavaBindingsRec(clazz, out, 0);

		out.flush();
		fos.flush();
	}
	
	private static boolean checkForPublicSignatureTypes(Class<?>[] params)
	{
		boolean allTypesArePublic = true;
		
		if (params != null)
		{
			for (Class<?> p: params)
			{
				int modifiers = p.getModifiers();
				
				if (!Modifier.isPublic(modifiers))
				{
					allTypesArePublic = false;
					break;
				}
			}
		}
		
		return allTypesArePublic;
	}

	private static boolean checkForPublicNestedInterfaces(Class<?> clazz)
	{
		boolean found = false;

		Class<?>[] declClasses = clazz.getDeclaredClasses();

		if (declClasses != null)
		{
			for (Class<?> c : declClasses)
			{
				int modifiers = c.getModifiers();

				if (Modifier.isPublic(modifiers) && c.isInterface())
				{
					found = true;
					break;
				}
			}
		}

		return found;
	}

	private static boolean checkForPublicNestedStaticClasses(Class<?> clazz)
	{
		boolean found = false;

		Class<?>[] declClasses = clazz.getDeclaredClasses();

		if (declClasses != null)
		{
			for (Class<?> c : declClasses)
			{
				int modifiers = c.getModifiers();

				if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && !c.isInterface())
				{

					Constructor<?>[] ctors = c.getConstructors();

					if ((ctors != null) && (ctors.length > 0))
					{
						found = true;
						break;
					}
				}
			}
		}

		return found;
	}

	private static boolean equalMethodSignatures(Method x, Method y)
	{
		if (x.equals(y))
			return true;

		if (!x.getName().equals(y.getName()))
			return false;

		Class<?>[] xParams = x.getParameterTypes();
		Class<?>[] yParams = y.getParameterTypes();

		if (xParams.length != yParams.length)
			return false;

		boolean result = true;

		for (int i = 0; i < xParams.length; i++)
		{
			if (!xParams[i].equals(yParams[i]))
			{
				result = false;
				break;
			}
		}

		return result;
	}

	private static String bridge = "com.tns.Platform.";

	private static void getEligibleMethodsHelper(Class<?> clazz, List<Method> methods, List<Method> finals)
	{
		Method[] declMethods = clazz.getDeclaredMethods();

		if (declMethods != null)
		{
			for (Method m : declMethods)
			{
				int modifiers = m.getModifiers();

				boolean isStaticMethod = Modifier.isStatic(modifiers);
				boolean isFinalMethod = Modifier.isFinal(modifiers);
				boolean isPublicMethod = Modifier.isPublic(modifiers);
				boolean isProtectedMethod = Modifier.isProtected(modifiers);
				boolean isVisible = isPublicMethod || isProtectedMethod;

				if (!isVisible)
					continue;

				if (isFinalMethod)
				{
					boolean found = false;
					for (Method finalMethod : finals)
					{
						if (equalMethodSignatures(m, finalMethod))
						{
							found = true;
							break;
						}
					}

					if (!found)
					{
						finals.add(m);
					}

					continue;
				}
				else if (!isStaticMethod)
				{
					boolean found = false;
					for (Method finalMethod : finals)
					{
						if (equalMethodSignatures(m, finalMethod))
						{
							found = true;
							break;
						}
					}

					if (found)
						continue;

					for (Method addedMethod : methods)
					{
						if (equalMethodSignatures(m, addedMethod))
						{
							found = true;
							break;
						}
					}

					if (!found)
					{
						methods.add(m);
					}
				}
			}
		}
	}

	private static List<Method> getEligibleMethods(Class<?> clazz)
	{
		List<Method> result = new ArrayList<Method>();
		List<Method> finals = new ArrayList<Method>();
		LinkedList<Class<?>> interfaces = new LinkedList<Class<?>>();

		LinkedList<Class<?>> q = new LinkedList<Class<?>>();
		q.addLast(clazz);

		Class<?> c;
		while ((c = q.pollFirst()) != null)
		{
			getEligibleMethodsHelper(c, result, finals);

			//
			Class<?>[] subs = c.getInterfaces();
			if (subs != null)
			{
				for (Class<?> sub : subs)
				{
					boolean found = false;
					for (Class<?> i : interfaces)
					{
						if (sub.equals(i))
						{
							found = true;
							break;
						}
					}
					if (!found)
					{
						interfaces.addLast(sub);
					}
				}
			}
			//

			Class<?> base = c.getSuperclass();
			if (base != null)
			{
				q.addLast(base);
			}
		}

		q.addLast(clazz);

		for (Class<?> i : interfaces)
		{
			q.addLast(i);
		}

		while ((c = q.pollFirst()) != null)
		{
			getEligibleMethodsHelper(c, result, finals);

			Class<?>[] subs = c.getInterfaces();
			if (subs != null)
			{
				for (Class<?> sub : subs)
				{
					q.addLast(sub);
				}
			}
		}

		return result;
	}

	private static void generateJavaBindingsRec(Class<?> clazz, OutputStreamWriter out, int level) throws Exception
	{
		int clazzModifiers = clazz.getModifiers();

		if (!Modifier.isPublic(clazzModifiers))
		{
			return;
		}
		
		boolean isFinalClass = Modifier.isFinal(clazzModifiers);

		String tabs = getTabsForLevel(level);

		if (isFinalClass)
		{
			if (level == 0)
			{
				out.write(tabs + "public final class " + clazz.getSimpleName() + " {\n");
			}
			else
			{
				out.write(tabs + "public static final class " + clazz.getSimpleName() + " {\n");
			}

			out.write(tabs + "\tprivate " + clazz.getSimpleName() + "() {\n");
			out.write(tabs + "\t}\n");

			for (Class<?> nested : clazz.getDeclaredClasses())
			{

				generateJavaBindingsRec(nested, out, level + 1);
			}
			
			out.write(tabs + "}\n");

			return;
		}

		boolean hasPublicCtors = false;

		if (clazz.isInterface())
		{
			hasPublicCtors = true;

			if (level > 0)
			{
				out.write(tabs + "public static class " + clazz.getSimpleName() + " implements " + clazz.getCanonicalName() + ", com.tns.NativeScriptHashCodeProvider {\n");
			}
			else
			{
				out.write(tabs + "public class " + clazz.getSimpleName() + " implements " + clazz.getCanonicalName() + ", com.tns.NativeScriptHashCodeProvider {\n");
			}
			
			for (Class<?> nested : clazz.getDeclaredClasses())
			{

				generateJavaBindingsRec(nested, out, level + 1);
			}
		}
		else
		{
			for (Constructor<?> c : clazz.getConstructors())
			{
				if (c.isSynthetic())
				{
					continue;
				}

				if (!hasPublicCtors)
				{
					if (Modifier.isStatic(clazzModifiers))
					{
						out.write(tabs + "public static class " + clazz.getSimpleName() + " extends " + clazz.getCanonicalName() + " implements com.tns.NativeScriptHashCodeProvider {\n");
					}
					else
					{
						out.write(tabs + "public class " + clazz.getSimpleName() + " extends " + clazz.getCanonicalName() + " implements com.tns.NativeScriptHashCodeProvider {\n");
					}
					hasPublicCtors = true;

					for (Class<?> nested : clazz.getDeclaredClasses())
					{
						generateJavaBindingsRec(nested, out, level + 1);
					}
				}

				writeConstructorSignature(out, level, c);
				writeExceptionSignature(out, c);
				writeConstructorBody(out, level, clazz, c);
			}
		}

		if (!hasPublicCtors)
		{
			return;
		}
		
		boolean isAndroidApplicationClass = isAndroidApplicationClass(clazz);
		
		if (isAndroidApplicationClass)
		{
			writeKimeraLoadLibraryStaticSection(out, level);
		}

		List<Method> methods2 = getEligibleMethods(clazz);

		Collections.sort(methods2, new Comparator<Method>()
		{
			@Override
			public int compare(Method x, Method y)
			{
				return x.getName().compareTo(y.getName());
			}
		});

		int methodGroupIdx = -1;
		ArrayList<String> methodGroups = new ArrayList<String>();
		String lastMethodGroupName = "";

		for (Method m : methods2)
		{
			if (m.isSynthetic())
			{
				continue;
			}
			
			int modifiers = m.getModifiers();

			boolean isFinal = Modifier.isFinal(modifiers);
			boolean isStatic = Modifier.isStatic(modifiers);
			boolean isAbstract = Modifier.isAbstract(modifiers);

			if (!isFinal && !isStatic)
			{
				//
				if (!m.getName().equals(lastMethodGroupName))
				{
					++methodGroupIdx;
					String currentMethodName = m.getName();
					methodGroups.add(currentMethodName);
					lastMethodGroupName = currentMethodName;
				}
				//

				Map<Type, Type> map = getGenericParentsMap2(clazz);

				Type genRetType = m.getGenericReturnType();

				Type res = genRetType;
				while (map.containsKey(res))
				{
					res = map.get(res);
				}

				String retType;
				if (res instanceof Class)
				{
					retType = ((Class<?>) res).getCanonicalName();
				}
				else
				{
					retType = m.getReturnType().getCanonicalName();
				}

				Type[] genTypes = m.getGenericParameterTypes();
				
				boolean isAndroidApplicationOnCreateMethod = isAndroidApplicationClass && m.getName().equals("onCreate");
				
				String methodName = isAndroidApplicationOnCreateMethod ? "onCreateInternal" : null;

				writeMethodSignature(out, level, m, retType, map, genTypes, methodName);
				writeExceptionSignature(out, m);

				if (clazz.isInterface())
				{
					writeInterfaceMethodImplementation(out, level, m, retType);
				}
				else
				{
					if (isAbstract)
					{
						writeAbstractMethodImplementation(out, level, m, retType);
					}
					else
					{
						writeMethodBody(out, level, clazz, m, retType, methodGroupIdx);
					}
				}
				
				if (isAndroidApplicationOnCreateMethod)
				{
					writeMethodSignature(out, level, m, retType, map, genTypes, null);
					writeExceptionSignature(out, m);

					writeAndroidApplicationOnCreateMethodBody(out, level);
				}

				if (isAbstract)
					continue;
			}
		}

		writeNativeScriptHashCodeProviderMethods(out, level, clazz, methodGroupIdx, methodGroups);

		// close class/interface
		out.write(tabs + "}\n");
	}
	
	private static void writeNativeScriptHashCodeProviderMethods(OutputStreamWriter out, int level, Class<?> clazz, int methodGroupIdx, ArrayList<String> methodGroups) throws Exception
	{
		String tabs = getTabsForLevel(level);
		
		out.write(tabs + "\tpublic boolean equals__super(java.lang.Object other) {\n");
		out.write(tabs + "\t\treturn super.equals(other);\n");
		out.write(tabs + "\t}\n");

		out.write(tabs + "\tpublic int hashCode__super() {\n");
		out.write(tabs + "\t\treturn super.hashCode();\n");
		out.write(tabs + "\t}\n");

		if (clazz.isInterface())
		{
			out.write(tabs + "\tpublic void setNativeScriptOverrides(java.lang.String[] overrides) {\n");
			out.write(tabs + "\t}\n");
		}
		else
		{
			writeSetKimeraOverrides(out, level, clazz, methodGroupIdx, methodGroups);
		}
	}

	private static void writeConstructorSignature(OutputStreamWriter out, int level, Constructor<?> c) throws Exception
	{
		String tabs = getTabsForLevel(level);

		Class<?> declClass = c.getDeclaringClass();

		out.write(tabs + "\tpublic " + declClass.getSimpleName());
		out.write("(");
		Class<?>[] paramTypes = c.getParameterTypes();

		int modifiers = declClass.getModifiers();

		boolean isStaticType = Modifier.isStatic(modifiers);
		boolean isNestedType = declClass.getEnclosingClass() != null;

		int startIndex = (isNestedType && !isStaticType) ? 1 : 0;
		for (int i = startIndex; i < paramTypes.length; i++)
		{
			if (i > startIndex)
			{
				out.write(", ");
			}
			out.write(paramTypes[i].getCanonicalName() + " param_" + i);
		}
		out.write(")");
	}

	private static void writeMethodSignature(OutputStreamWriter out, int level, Method m, String retType, Map<Type, Type> map, Type[] genTypes, String methodName) throws Exception
	{
		String tabs = getTabsForLevel(level);

		Class<?>[] paramTypes = m.getParameterTypes();

		int modifiers = m.getModifiers();

		boolean isPublic = Modifier.isPublic(modifiers);
		
		if (isPublic)
		{
			out.write(tabs + "\tpublic " + retType + " " + ((methodName == null) ? m.getName() : methodName));
		}
		else
		{
			out.write(tabs + "\tprotected " + retType + " " + ((methodName == null) ? m.getName() : methodName));
		}
		out.write("(");

		for (int i = 0; i < genTypes.length; i++)
		{
			if (i > 0)
			{
				out.write(", ");
			}
			if (genTypes[i] instanceof TypeVariable)
			{
				Type res = genTypes[i];
				while (map.containsKey(res))
				{
					res = map.get(res);
				}
				if (res instanceof Class)
				{
					out.write(((Class<?>) res).getCanonicalName() + " param_" + i);
				}
				else
				{
					out.write(paramTypes[i].getCanonicalName() + " param_" + i);
				}
			}
			else
			{
				out.write(paramTypes[i].getCanonicalName() + " param_" + i);
			}
		}
		out.write(")");
	}
	
	private static void writeAndroidApplicationOnCreateMethodBody(OutputStreamWriter out, int level) throws Exception
	{
		String tabs = getTabsForLevel(level);
		
		out.write(" {\n");
		out.write(tabs + "\t\tcom.tns.Platform.onCreateApplication(this);\n");
		out.write(tabs + "\t}\n\n");
	}

	private static void writeMethodBody(OutputStreamWriter out, int level, Class<?> clazz, Method m, String retType, int methodGroupIdx) throws Exception
	{
		String tabs = getTabsForLevel(level);

		Class<?>[] paramTypes = m.getParameterTypes();

		out.write(" {\n");

		int outIdx = methodGroupIdx / 8;
		int inIdx = methodGroupIdx % 8;

		if (checkIfMustWriteInitializationSection(clazz, m)) 
		{
			boolean shouldInitializeWithIntent = clazz.getName().equals("android.app.Activity")
												&& m.getName().equals("onCreate");
			
			writeCheckForInitialization(out, level, shouldInitializeWithIntent);
		}

		out.write(tabs + "\t\tif ((__ho" + outIdx + " & (1 << " + inIdx + ")) > 0) { \n");
		if (paramTypes.length == 0)
		{
			out.write(tabs + "\t\t\tjava.lang.Object[] params = null;\n");
		}
		else
		{
			out.write(tabs + "\t\t\tjava.lang.Object[] params = new Object[" + paramTypes.length + "];\n");
			for (int i = 0; i < paramTypes.length; i++)
			{
				out.write(tabs + "\t\t\tparams[" + i + "] = param_" + i + ";\n");
			}
		}
		out.write(tabs + "\t\t\t");
		if (retType != "void")
		{
			String wrappedRetType = wrapPrimitiveType(retType);
			out.write("return (" + wrappedRetType + ")");
		}
		if (m.getName().equals("init"))
		{
			out.write(bridge + "callJSMethod(this, \"" + m.getName() + "\", false, params);\n");
		}
		else
		{
			out.write(bridge + "callJSMethod(this, \"" + m.getName() + "\", params);\n");
		}
		out.write(tabs + "\t\t} else {\n");

		out.write(tabs + "\t\t\t");
		if (retType != "void")
		{
			out.write("return ");
		}
		out.write("super." + m.getName());
		out.write("(");
		for (int i = 0; i < paramTypes.length; i++)
		{
			if (i > 0)
			{
				out.write(", ");
			}
			out.write("param_" + i);
		}
		out.write(");\n");

		out.write(tabs + "\t\t}\n");

		out.write(tabs + "\t}\n\n");

	}

	
	private static boolean checkIfMustWriteInitializationSection(Class<?> clazz, Constructor<?> c)
	{
		String className = clazz.getCanonicalName();
		
		if (className.equals("android.app.Activity"))
		{
			return false;
		}
		else if (isAndroidApplicationClass(clazz))
		{
			return false;
		}
		
		return true;
	}
	
	private static boolean checkIfMustWriteInitializationSection(Class<?> clazz, Method m)
	{
		String className = clazz.getCanonicalName();
		
		if (className.equals("android.app.Activity"))
		{
			String methodName = m.getName();
			
			if (methodName.equals("attachBaseContext")
				|| methodName.equals("getSystemService")
				|| methodName.equals("getBaseContext")
				|| methodName.equals("setTheme")
				|| methodName.equals("getResources")
				|| methodName.equals("getApplicationInfo")
				|| methodName.equals("onApplyThemeResource"))
			{
				return false;
			}
		}
		else if (className.equals("android.app.Application"))
		{
			return false;
		}
		
		return true;
	}

	private static void writeConstructorBody(OutputStreamWriter out, int level, Class<?> clazz, Constructor<?> c) throws Exception
	{
		String tabs = getTabsForLevel(level);

		Class<?> declClass = c.getDeclaringClass();

		int modifiers = declClass.getModifiers();

		boolean isStaticType = Modifier.isStatic(modifiers);
		boolean isNestedType = declClass.getEnclosingClass() != null;

		Class<?>[] paramTypes = c.getParameterTypes();

		int startIndex = (isNestedType && !isStaticType) ? 1 : 0;

		out.write(" {\n");
		out.write(tabs + "\t\tsuper(");
		for (int i = startIndex; i < paramTypes.length; i++)
		{
			if (i > startIndex)
			{
				out.write(", ");
			}
			out.write("param_" + i);
		}
		out.write(");\n");

		if (checkIfMustWriteInitializationSection(clazz, c))
		{
			writeCheckForInitialization(out, level, false /* shouldInitializedWithIntent */);
		}

		out.write(tabs + "\t\tif (__ctorOverridden) {\n");
		int len1 = paramTypes.length - startIndex;
		if (len1 == 0)
		{
			out.write(tabs + "\t\t\tjava.lang.Object[] params = null;\n");
		}
		else
		{
			out.write(tabs + "\t\t\tjava.lang.Object[] params = new Object[" + paramTypes.length + "];\n");
			for (int i = startIndex; i < paramTypes.length; i++)
			{
				out.write(tabs + "\t\t\tparams[" + i + "] = param_" + i + ";\n");
			}
		}
		out.write(tabs + "\t\t\tcom.tns.Platform.callJSMethod(this, \"init\", true, params);\n");
		out.write(tabs + "\t\t}\n");
		out.write(tabs + "\t}\n\n");

	}
	
	private static void writeCheckForInitialization(OutputStreamWriter out, int level, boolean shouldInitializedWithIntent) throws Exception
	{
		String tabs = getTabsForLevel(level);
		
		out.write(tabs + "\t\tif (!__initialized) {\n");
		out.write(tabs + "\t\t\t__initialized = true;\n");
		if (shouldInitializedWithIntent)
		{
			out.write(tabs + "\t\t\tcom.tns.Platform.initInstance(this, super.getIntent());\n");
		}
		else
		{
			out.write(tabs + "\t\t\tcom.tns.Platform.initInstance(this);\n");
		}
		out.write(tabs + "\t\t}\n");
	}

	private static void writeExceptionSignature(OutputStreamWriter out, Constructor<?> c) throws Exception
	{
		Class<?>[] exc = c.getExceptionTypes();
		if (exc.length > 0)
		{
			out.write(" throws ");
			for (int i = 0; i < exc.length; i++)
			{
				if (i > 0)
				{
					out.write(", ");
				}
				out.write(exc[i].getCanonicalName());
			}
		}
	}

	private static void writeExceptionSignature(OutputStreamWriter out, Method m) throws Exception
	{
		Class<?>[] exc = m.getExceptionTypes();
		if (exc.length > 0)
		{
			out.write(" throws ");
			for (int i = 0; i < exc.length; i++)
			{
				if (i > 0)
				{
					out.write(", ");
				}
				out.write(exc[i].getCanonicalName());
			}
		}
	}

	private static void writeInterfaceMethodImplementation(OutputStreamWriter out, int level, Method m, String retType) throws Exception
	{
		out.write(" {\n");

		Class<?>[] paramTypes = m.getParameterTypes();

		String tabs = getTabsForLevel(level);

		if (paramTypes.length == 0)
		{
			out.write(tabs + "\t\tjava.lang.Object[] params = null;\n");
		}
		else
		{
			out.write(tabs + "\t\tjava.lang.Object[] params = new Object[" + paramTypes.length + "];\n");
			for (int i = 0; i < paramTypes.length; i++)
			{
				out.write(tabs + "\t\tparams[" + i + "] = param_" + i + ";\n");
			}
		}
		out.write(tabs + "\t\t");
		if (retType != "void")
		{
			String wrappedRetType = wrapPrimitiveType(retType);
			out.write("return (" + wrappedRetType + ")");
		}
		out.write(bridge + "callJSMethod(this, \"" + m.getName() + "\", params);\n");

		out.write(tabs + "\t}\n\n");
	}

	private static void writeAbstractMethodImplementation(OutputStreamWriter out, int level, Method m, String retType) throws Exception
	{
		out.write(" {\n");

		Class<?>[] paramTypes = m.getParameterTypes();

		String tabs = getTabsForLevel(level);

		if (paramTypes.length == 0)
		{
			out.write(tabs + "\t\tjava.lang.Object[] params = null;\n");
		}
		else
		{
			out.write(tabs + "\t\tjava.lang.Object[] params = new Object[" + paramTypes.length + "];\n");
			for (int i = 0; i < paramTypes.length; i++)
			{
				out.write(tabs + "\t\tparams[" + i + "] = param_" + i + ";\n");
			}
		}
		out.write(tabs + "\t\t");
		if (retType != "void")
		{
			String wrappedRetType = wrapPrimitiveType(retType);
			out.write("return (" + wrappedRetType + ")");
		}
		out.write(bridge + "callJSMethod(this, \"" + m.getName() + "\", params);\n");

		out.write(tabs + "\t}\n\n");
	}

	private static boolean isAndroidApplicationClass(Class<?> clazz)
	{
		return clazz.getCanonicalName().equals("android.app.Application");
	}
	
	private static void writeKimeraLoadLibraryStaticSection(OutputStreamWriter out, int level) throws Exception
	{
		String tabs = getTabsForLevel(level);
	
		out.write(tabs + "\tstatic {\n");
		out.write(tabs + "\t\tSystem.loadLibrary(\"NativeScript\");\n");
		out.write(tabs + "\t\tif (BuildConfig.DEBUG) {\n");
		out.write(tabs + "\t\t\tandroid.os.Debug.waitForDebugger();\n");
		out.write(tabs + "\t\t}\n");
		out.write(tabs + "\t}\n\n");
	}

	private static void writeSetKimeraOverrides(OutputStreamWriter out, int level, Class<?> clazz, int methodGroupIdx, List<String> methodGroups) throws Exception
	{
		String tabs = getTabsForLevel(level);

		out.write(tabs + "\tpublic void setNativeScriptOverrides(java.lang.String[] overrides) {\n");
		out.write(tabs + "\t\tfor (java.lang.String name: overrides) {\n");
		out.write(tabs + "\t\t\tif (name.equals(\"init\")) {\n");
		out.write(tabs + "\t\t\t\t__ctorOverridden = true;\n");
		out.write(tabs + "\t\t\t}\n");
		for (int i = 0; i < methodGroups.size(); i++)
		{
			String methodName = methodGroups.get(i);
			int outIdx = i / 8;
			int inIdx = i % 8;

			if (i == 0)
			{
				out.write(tabs + "\t\t\tif (name.equals(\"" + methodName + "\")) {\n");
			}
			else
			{
				out.write(tabs + "\t\t\t} else if (name.equals(\"" + methodName + "\")) {\n");
			}
			out.write(tabs + "\t\t\t\t__ho" + outIdx + " |= (1 << " + inIdx + ");\n");
		}
		out.write(tabs + "\t\t\t}\n");
		out.write(tabs + "\t\t}\n");
		out.write(tabs + "\t}\n");

		if (isAndroidApplicationClass(clazz))
		{
			out.write(tabs + "\tprivate boolean __initialized = true;\n");
		}
		else
		{
			out.write(tabs + "\tprivate boolean __initialized;\n");
		}
		out.write(tabs + "\tprivate boolean __ctorOverridden;\n");
		int lastMethodGroupIdx = (methodGroupIdx / 8);
		if ((methodGroupIdx % 8) > 0)
		{
			++lastMethodGroupIdx;
		}
		for (int i = 0; i <= lastMethodGroupIdx; i++)
		{
			out.write(tabs + "\tprivate byte __ho" + i + ";\n");
		}
	}
	
	private static String getTabsForLevel(int level)
	{
		String tabs = "";
		for (int i = 0; i < level; i++)
		{
			tabs += "\t";
		}

		return tabs;
	}

	private static boolean isMethodOverrriden(final Method myMethod) {
	    Class<?> declaringClass = myMethod.getDeclaringClass();
	    Class<?>[] myMethodParams = myMethod.getParameterTypes();
	    if (declaringClass.equals(Object.class)) {
	        return false;
	    }
	    try {
	    	Class<?> sup = declaringClass.getSuperclass();
	    	Method[] dm = sup.getDeclaredMethods();
	    	for (Method m: dm)
	    	{
	    		if (!m.getName().equals(myMethod.getName()))
	    			continue;

	    		Class<?>[] mp = m.getParameterTypes();
	    		if (mp.length != myMethodParams.length)
	    			continue;
	    		
	    		boolean same = true;
	    		for (int i=0; i<mp.length; i++)
	    		{
	    			if (!mp[i].equals(myMethodParams[i]))
	    			{
	    				same = false;
	    				break;
	    			}
	    		}
	    		if (same)
	    			return true;
	    	}
	    	declaringClass.getSuperclass().getMethod(myMethod.getName(), myMethod.getParameterTypes());
	        return true;
	    } catch (NoSuchMethodException e) {
	        for (Class<?> iface : declaringClass.getInterfaces()) {
	            try {
	                iface.getMethod(myMethod.getName(), myMethod.getParameterTypes());
	                return true;
	            } catch (NoSuchMethodException ignored) {

	            }
	        }
	        return false;
	    }
	}

	private static void generateMetadata(Class<?> clazz, List<String> knownMetadataTypes, TreeNode root) throws Exception
	{
		int classModifiers = clazz.getModifiers();

		if (!Modifier.isPublic(classModifiers))
		{
			return;
		}
		
		String typeName = clazz.getName();
		
		knownMetadataTypes.add(typeName);

		TreeNode node = getOrCreateNode(root, clazz);

		// Method[] methods = clazz.getDeclaredMethods();
		Method[] tmp = clazz.getMethods();
		ArrayList<Method> methods = new ArrayList<Method>();
		for (Method m : tmp)
		{
			methods.add(m);
		}
		Class<?> curClass = clazz;
		while (curClass != null)
		{
			tmp = curClass.getDeclaredMethods();
			for (Method m : tmp)
			{
				int modifiers = m.getModifiers();
				if (Modifier.isProtected(modifiers) && !Modifier.isStatic(modifiers))
				{
					if (isMethodOverrriden(m))
						continue;
					
					methods.add(m);
				}
			}
			curClass = curClass.getSuperclass();
		}
		
		if (clazz.isInterface())
		{
			for (Method m: Object.class.getDeclaredMethods())
			{
				methods.add(m);
			}
		}
		
		Collections.sort(methods, methodNameComparator);
		
		for (Method m : methods)
		{
			if (m.isSynthetic())
				continue;
			
			int modifiers = m.getModifiers();
			if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers))
			{
				boolean isStatic = Modifier.isStatic(modifiers);
				
				MethodInfo mi = new MethodInfo(m.getName());
				
				Class<?>[] params = m.getParameterTypes();
				mi.signature = getMethodSignature(root, m.getReturnType(), params);// + " " + params.length);

				if (isStatic)
				{
					mi.declaringType = getOrCreateNode(root, m.getDeclaringClass());
					node.staticMethods.add(mi);
				}
				else
				{
					node.instanceMethods.add(mi);
				}
			}
		}

		Field[] fields = clazz.getFields();

		for (Field f : fields)
		{
			int modifiers = f.getModifiers();
			if (Modifier.isPublic(modifiers))
			{
				boolean isStatic = Modifier.isStatic(modifiers);
				
				FieldInfo fi = new FieldInfo(f.getName());

				Class<?> type = f.getType();
				fi.valueType = getOrCreateNode(root, type);

				if (isStatic)
				{
					fi.declaringType = getOrCreateNode(root, f.getDeclaringClass());
					node.staticFields.add(fi);
				}
				else
				{
					node.instanceFields.add(fi);
				}
			}
		}
	}
	
	public static Map<Type, Type> getGenericParentsMap2(Class<?> clazz)
	{
		List<Type> parents = new ArrayList<Type>();

		Class<?> oldParent = clazz;
		Class<?> parentClass = clazz.getSuperclass();

		while ((parentClass != null) && (parentClass != Object.class))
		{
			Type nextParent = oldParent.getGenericSuperclass();
			parents.add(nextParent);
			oldParent = parentClass;
			parentClass = parentClass.getSuperclass();
		}

		Map<Type, Type> map = new HashMap<Type, Type>();

		for (Type p : parents)
		{
			if (p instanceof ParameterizedType)
			{
				ParameterizedType pt = (ParameterizedType) p;
				Type[] typeParameters = ((Class<?>) pt.getRawType()).getTypeParameters();
				Type[] actualTypeArgs = pt.getActualTypeArguments();
				for (int i = 0; i < typeParameters.length; i++)
				{
					map.put(typeParameters[i], actualTypeArgs[i]);
				}
			}
		}
		return map;
	}

	private static String wrapPrimitiveType(String type)
	{
		String wrapped;

		if (type.equals("boolean") || type.equals("byte") || type.equals("short") || type.equals("long") || type.equals("float") || type.equals("double"))
		{
			wrapped = type.substring(0, 1).toUpperCase() + type.substring(1);
		}
		else if (type.equals("char"))
		{
			wrapped = "Character";
		}
		else if (type.equals("int"))
		{
			wrapped = "Integer";
		}
		else
		{
			wrapped = type;
		}

		return wrapped;
	}
		
	private static void writeKnownMetadata(List<String> knownMetadata) throws Exception
	{
		FileOutputStream fos = new FileOutputStream("bin/out/__Metadata.java");
		OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");
		
		out.write("package com.tns;\n\n");
		
		out.write("import java.util.HashSet;\n\n");
		
		out.write("class Metadata {\n");
		out.write("\tprivate static HashSet<String> knownMetadata = new HashSet<String>();\n\n");
		
		out.write("\tpublic static HashSet<String> getKnownMetadata() {\n");
		
		out.write("\t\t if (knownMetadata.isEmpty()) {\n");
		
		for (String typeName: knownMetadata)
		{
			out.write("\t\t\tknownMetadata.add(\"" + typeName + "\");\n");
		}
		out.write("\t\t}\n");
		
		out.write("\t\treturn knownMetadata;\n");
			
		out.write("\t}\n");
		
		out.write("}");
		
		out.close();
		fos.close();
	}
	
	public static void startGenerateMetadata(String[] args, String[] jars) throws Exception
	{
		String[] outDirs = new String[jars.length];
		
		for (int i=0; i<jars.length; i++)
		{
			String jar = jars[i];
			String[] dirs = { "out/", jar + "/", "metadata/" };
			String outputDir = "bin/";
			for (String d: dirs)
			{
				outputDir += d;
				File fd = new File(outputDir);
				if (!fd.exists())
				{
					fd.mkdir();
				}
			}
			outDirs[i] = outputDir;
		}

		start(args, jars, outDirs, true /* metadata */);
	}
	
	public static void startGenerateBindings(String[] args, String[] jars) throws Exception
	{
		String[] outDirs = new String[jars.length];
		
		for (int i=0; i<jars.length; i++)
		{
			String jar = jars[i];
			String[] dirs = { "out/", jar + "/", "com/", "tns/" };
			String outputDir = "bin/";
			for (String d: dirs)
			{
				outputDir += d;
				File fd = new File(outputDir);
				if (!fd.exists())
				{
					fd.mkdir();
				}
			}
			outDirs[i] = outputDir;
		}
		
		start(args, jars, outDirs, false /* metadata */);
	}

	private static void start(String[] args, String[] jars, String[] outDirs, boolean generateMetadata) throws Exception
	{
		List<String> knownMetadataTypes = new ArrayList<String>();
		
		TreeNode root = new TreeNode();
		root.setName("");
		root.children.add(TreeNode.BYTE);
		root.children.add(TreeNode.SHORT);
		root.children.add(TreeNode.INTEGER);
		root.children.add(TreeNode.LONG);
		root.children.add(TreeNode.FLOAT);
		root.children.add(TreeNode.DOUBLE);
		root.children.add(TreeNode.BOOLEAN);
		root.children.add(TreeNode.CHAR);
		
		for (int i=0; i<jars.length; i++)
		{
			String jarFile = jars[i];
			String outDir = outDirs[i];
			
			JarInputStream input = null;
			
			try
			{
				String jarFilename = "jars/" + jarFile;
				input = new JarInputStream(new FileInputStream(jarFilename));
	
				JarEntry entry = input.getNextJarEntry();
				ArrayList<String> classes = new ArrayList<String>();
				while (entry != null)
				{
					try
					{
						String name = entry.getName();

						if (!name.endsWith(".class"))
							continue;

						name = name.substring(0, name.length() - 6).replace('/', '.');
						// System.out.println(name);
						
						classes.add(name);
					}
					finally
					{
						entry = input.getNextJarEntry();
					}
				}
	
				//
				Collections.sort(classes);
				//
	
				ClassLoader loader = URLClassLoader.newInstance(new URL[]
				{ new URL("file://" + jarFile) }, ClassLoader.getSystemClassLoader());
	
				//File root1 = new File(".");
				//URLClassLoader loader = URLClassLoader.newInstance(new URL[] { root1.toURI().toURL() });
				
				for (String className : classes)
				{
					//
					if (jarFile.equals("nativescript.jar")
						&& className.startsWith("com.tns.com.tns.tests."))
					{
						continue;
					}
					//
					Class<?> clazz = Class.forName(className, false, loader);
					
					if (generateMetadata)
					{
						generateMetadata(clazz, knownMetadataTypes, root);
					}
					else
					{
						generateJavaBindings(clazz, outDir);
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			finally
			{
				if (input != null)
				{
					input.close();
				}
			}
		}
		
		if (generateMetadata)
		{
			if (root.children.size() > 0)
			{
				writeTree(root);
			}
			
			if (knownMetadataTypes.size() > 0)
			{
				writeKnownMetadata(knownMetadataTypes);
			}
		}
	}
	
	private static byte[] writeUniqueName_lenBuff = new byte[2];
	
	private static int writeUniqueName(String name, HashMap<String, Integer> uniqueStrings, FileOutputStream outStringsStream) throws Exception
	{
		int position = (int) outStringsStream.getChannel().position();
		
		int len = name.length();
		writeUniqueName_lenBuff[0] = (byte) (len & 0xFF);
		writeUniqueName_lenBuff[1] = (byte) ((len >> 8) & 0xFF);
		outStringsStream.write(writeUniqueName_lenBuff);
		outStringsStream.write(name.getBytes("UTF-8"));

		uniqueStrings.put(name, position);
		
		return position;
	}
	
	private static byte[] writeInt_buff = new byte[4];
	
	private static void writeInt(int value, FileOutputStream out) throws Exception
	{
		writeInt_buff[0] = (byte)(value & 0xFF);
		writeInt_buff[1] = (byte)((value >> 8) & 0xFF);
		writeInt_buff[2] = (byte)((value >> 16) & 0xFF);
		writeInt_buff[3] = (byte)((value >> 24) & 0xFF);
		
		out.write(writeInt_buff);
	}
	
	private static void writeMethodInfo(MethodInfo mi, HashMap<String, Integer> uniqueStrings, FileOutputStream outValueStream) throws Exception
	{
		int pos = uniqueStrings.get(mi.name).intValue();
		writeInt(pos, outValueStream);
		
		int sigLen = writeLength(mi.signature.size(), outValueStream);
		for (int i=0; i<sigLen; i++)
		{
			TreeNode arg = mi.signature.get(i);
			
			writeTreeNodeId(arg, outValueStream);
		}
	}
	
	private static byte[] writeTreeNodeId_buff = new byte[2];
	
	private static void writeTreeNodeId(TreeNode node, FileOutputStream out) throws Exception
	{
		if (node == null)
		{
			writeTreeNodeId_buff[0] = writeTreeNodeId_buff[1] = 0;
		}
		else
		{
			writeTreeNodeId_buff[0] = (byte) (node.id & 0xFF);
			writeTreeNodeId_buff[1] = (byte) ((node.id >> 8) & 0xFF);
		}
		out.write(writeTreeNodeId_buff);
	}
	
	private static byte[] writeLength_lenBuff = new byte[2];
	
	private static int writeLength(int length, FileOutputStream out) throws Exception
	{
		writeLength_lenBuff[0] = (byte) (length & 0xFF);
		writeLength_lenBuff[1] = (byte) ((length >> 8) & 0xFF);
		out.write(writeLength_lenBuff);
		
		return length;
	}
	
	private static void writeTree(TreeNode root) throws Exception
	{
		short curId = 0;

		ArrayDeque<TreeNode> d = new ArrayDeque<TreeNode>();
		
		FileOutputStream outStringsStream = new FileOutputStream("bin/treeStringsStream.dat");
		
		HashMap<String, Integer> uniqueStrings = new HashMap<String, Integer>();
		
		int commonInterfacePrefixPosition = writeUniqueName("com/tns/", uniqueStrings, outStringsStream);
		
		d.push(root);
		while (!d.isEmpty())
		{
			TreeNode n = d.pollFirst();
			n.id = n.firstChildId = n.nextSiblingId = curId++;
			
			String name = n.getName();
			
			if (uniqueStrings.containsKey(name))
			{
				n.offsetName = uniqueStrings.get(name).intValue();
			}
			else
			{
				n.offsetName = writeUniqueName(name, uniqueStrings, outStringsStream);
			}
			
			if (((n.nodeType & TreeNode.Interface) == TreeNode.Interface)
				|| ((n.nodeType & TreeNode.Class) == TreeNode.Class))
			{
				for (int i=0; i<n.instanceMethods.size(); i++)
				{
					name = n.instanceMethods.get(i).name;
					if (!uniqueStrings.containsKey(name))
					{
						int pos = writeUniqueName(name, uniqueStrings, outStringsStream);
					}
				}
				for (int i=0; i<n.staticMethods.size(); i++)
				{
					name = n.staticMethods.get(i).name;
					if (!uniqueStrings.containsKey(name))
					{
						int pos = writeUniqueName(name, uniqueStrings, outStringsStream);
					}
				}
				for (int i=0; i<n.instanceFields.size(); i++)
				{
					name = n.instanceFields.get(i).name;
					if (!uniqueStrings.containsKey(name))
					{
						int pos = writeUniqueName(name, uniqueStrings, outStringsStream);
					}
				}
				for (int i=0; i<n.staticFields.size(); i++)
				{
					name = n.staticFields.get(i).name;
					if (!uniqueStrings.containsKey(name))
					{
						int pos = writeUniqueName(name, uniqueStrings, outStringsStream);
					}
				}
			}
			
			for (TreeNode child: n.children)
				d.add(child);
		}
		
		outStringsStream.flush();
		outStringsStream.close();
		
		FileOutputStream outValueStream = new FileOutputStream("bin/treeValueStream.dat");
		writeInt(0, outValueStream);
		
		final int array_offset = 1000 * 1000 * 1000;
		
		d.push(root);
		while (!d.isEmpty())
		{
			TreeNode n = d.pollFirst();
			
			if (n.nodeType == TreeNode.Package)
			{
				n.offsetValue = 0;
			}
			else if ((n.nodeType & TreeNode.Primitive) == TreeNode.Primitive)
			{
				n.offsetValue = (int) outValueStream.getChannel().position();
				
				outValueStream.write(n.nodeType);
			}
			else if (((n.nodeType & TreeNode.Class) == TreeNode.Class)
					|| ((n.nodeType & TreeNode.Interface) == TreeNode.Interface))
			{
				n.offsetValue = (int) outValueStream.getChannel().position();
				
				outValueStream.write(n.nodeType);
				
				//
				writeTreeNodeId(n.baseClassNode, outValueStream);
				//
				
				if ((n.nodeType & TreeNode.Interface) == TreeNode.Interface)
				{
					byte usePrefix = true ? 1 : 0;
					outValueStream.write(usePrefix);
					writeInt(commonInterfacePrefixPosition, outValueStream);
				}
				
				int len = writeLength(n.instanceMethods.size(), outValueStream);
				for (int i=0; i<len; i++)
				{
					writeMethodInfo(n.instanceMethods.get(i), uniqueStrings, outValueStream);
				}
				
				len = writeLength(n.staticMethods.size(), outValueStream);
				for (int i=0; i<len; i++)
				{
					MethodInfo mi = n.staticMethods.get(i);
					writeMethodInfo(mi, uniqueStrings, outValueStream);
					writeTreeNodeId(mi.declaringType, outValueStream);
				}
				
				len = writeLength(n.instanceFields.size(), outValueStream);
				for (int i=0; i<len; i++)
				{
					FieldInfo fi = n.instanceFields.get(i);
					int pos = uniqueStrings.get(fi.name).intValue();
					writeInt(pos, outValueStream);
					writeTreeNodeId(fi.valueType, outValueStream);
				}

				len = writeLength(n.staticFields.size(), outValueStream);
				for (int i=0; i<len; i++)
				{
					FieldInfo fi = n.staticFields.get(i);
					int pos = uniqueStrings.get(fi.name).intValue();
					writeInt(pos, outValueStream);
					writeTreeNodeId(fi.valueType, outValueStream);
					writeTreeNodeId(fi.declaringType, outValueStream);
				}
			}
			else if ((n.nodeType & TreeNode.Array) == TreeNode.Array)
			{
				n.offsetValue = array_offset;
			}
			else
			{
				throw new Exception("should not happen");
			}
			
			for (TreeNode child: n.children)
				d.add(child);
		}
		
		
		outValueStream.flush();
		outValueStream.close();
		
		d.push(root);
		while (!d.isEmpty())
		{
			TreeNode n = d.pollFirst();
			
			if (n.arrayElement != null)
			{
				n.offsetValue = array_offset + n.arrayElement.id;
			}
			
			if (!n.children.isEmpty())
				n.firstChildId = n.children.get(0).id;
			
			for (int i=0; i<n.children.size(); i++)
			{
				if (i > 0)
					n.children.get(i - 1).nextSiblingId = n.children.get(i).id;
				
				d.add(n.children.get(i));
			}
		}
		
		FileOutputStream outNodeStream = new FileOutputStream("bin/treeNodeStream.dat");
		int[] nodeData = new int[3];
		
		ByteBuffer byteBuffer = ByteBuffer.allocate(nodeData.length * 4);
		byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();

		d.push(root);
		while (!d.isEmpty())
		{
			TreeNode n = d.pollFirst();
			
			nodeData[0] = n.firstChildId + (n.nextSiblingId << 16);
			nodeData[1] = n.offsetName;
			nodeData[2] = n.offsetValue;
			
			intBuffer.clear();
	        intBuffer.put(nodeData);
			outNodeStream.write(byteBuffer.array());
			
			for (TreeNode child: n.children)
				d.add(child);
		}

		outNodeStream.flush();
		outNodeStream.close();
	}
}
