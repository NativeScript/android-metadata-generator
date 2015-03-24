package com.telerik.metadata;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import com.telerik.metadata.TreeNode.FieldInfo;
import com.telerik.metadata.TreeNode.MethodInfo;

public class Builder {
	private static class MethodNameComparator implements Comparator<Method>
	{
		@Override
		public int compare(Method o1, Method o2) {
			return o1.getName().compareTo(o2.getName());
		}
	}
	
	private static MethodNameComparator methodNameComparator = new MethodNameComparator();
	private static HashMap<Class<?>, String> jniPrimitiveTypesMappings = new HashMap<Class<?>, String>();
	
	public static TreeNode build(String dir) throws Exception{
		NSClassLoader loader = NSClassLoader.getInstance();
		loader.loadDir(dir);
		initialize();
		
		TreeNode root = TreeNode.getRoot();
		
		ArrayList<ArrayList<String>> jarClassNames = NSClassLoader.getInstance().getClassNames();
		for(ArrayList<String> classNames : jarClassNames){
			for(String className : classNames){
				try 
				{
					// possible exceptions here are:
					// - NoClassDefFoundError
					// - ClassNotFoundException
					// both are raised due to some API level mismatch - 
					// e.g. we are processing jars with API 21 while we have in our class path API 17
					Class<?> clazz = Class.forName(className, false, loader);
					generate(clazz, root);
				} 
				catch (Throwable e)
				{
					System.out.println("Skip " + className);
				}
			}
		}
		
		return root;
	}
	
	private static void initialize(){
		jniPrimitiveTypesMappings.put(byte.class, "B");
		jniPrimitiveTypesMappings.put(short.class, "S");
		jniPrimitiveTypesMappings.put(int.class, "I");
		jniPrimitiveTypesMappings.put(long.class, "J");
		jniPrimitiveTypesMappings.put(float.class, "F");
		jniPrimitiveTypesMappings.put(double.class, "D");
		jniPrimitiveTypesMappings.put(boolean.class, "Z");
		jniPrimitiveTypesMappings.put(char.class, "C");
	}
	
	private static Boolean isClassPublic(Class<?> clazz){
		Boolean isPublic = true;
		
		try
		{
			Class<?> currClass = clazz;
			while(currClass != null)
			{
				if(!Modifier.isPublic(currClass.getModifiers()))
				{
					isPublic = false;
					break;
				}
				
				currClass = currClass.getEnclosingClass();
			}
		}
		catch(NoClassDefFoundError e)
		{
			isPublic = false;
		}
		
		return isPublic;
	}
	
	private static void generate(Class<?> clazz, TreeNode root) throws Exception
	{
		if (!isClassPublic(clazz))
		{
			return;
		}
		
		TreeNode node = getOrCreateNode(root, clazz);

		Method[] methods = clazz.getDeclaredMethods();
		
		Arrays.sort(methods, methodNameComparator);
		
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

		Field[] fields = clazz.getDeclaredFields();

		for (Field f : fields)
		{
			int modifiers = f.getModifiers();
			if (Modifier.isPublic(modifiers))
			{
				boolean isStatic = Modifier.isStatic(modifiers);
				boolean isFinal = Modifier.isFinal(modifiers);//TODO: plamen5kov revise later
				
 				FieldInfo fi = new FieldInfo(f.getName());

				Class<?> type = f.getType();
				fi.valueType = getOrCreateNode(root, type);
				fi.isFinalType = isFinal;
				
				if (isStatic) {
					fi.declaringType = getOrCreateNode(root, f.getDeclaringClass());					
					node.staticFields.add(fi);
				} else {
					node.instanceFields.add(fi);
				}
			}
		}
	}
	
	private static TreeNode getOrCreateNode(TreeNode root, Class<?> clazz) throws Exception
	{
		if (clazz.isPrimitive()) {
			return TreeNode.getPrimitive(clazz); 
		}
		
		if(clazz.isArray()){
			return createArrayNode(root, clazz);
		}
		
		TreeNode node = root;
		String name = clazz.getSimpleName();
		
		String[] packages = clazz.getPackage().getName().split("\\.");
		
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
		
		Class<?> outer = clazz.getEnclosingClass();
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
			if (clazz.isPrimitive())
			{
				TreeNode tmp = TreeNode.getPrimitive(clazz);
				child.nodeType = tmp.nodeType;
			}
			else
			{
				child.nodeType = clazz.isInterface() ? TreeNode.Interface : TreeNode.Class;
				int classModifilers = clazz.getModifiers();
				if (Modifier.isStatic(classModifilers))
				{
					child.nodeType |= TreeNode.Static;	
				}
			}
		}
		node = child;
		
		Class<?> baseClass = clazz.isInterface()
							? Object.class
							: clazz.getSuperclass();
		if (baseClass != null){
			node.baseClassNode = getOrCreateNode(root, baseClass);
		}
		
		return node;
	}
	
	private static TreeNode createArrayNode(TreeNode root, Class<?> clazz) throws Exception{
		TreeNode currentNode = root;
		Class<?> currentClass = clazz;
		
		while (currentClass.isArray())
		{
			TreeNode child = currentNode.getChild("[");
			if (child == null)
			{
				child = currentNode.createChild("[");
				child.nodeType = TreeNode.Array;
				child.offsetValue = 1;
			}
			currentClass = currentClass.getComponentType();
			currentNode = child;
		}
		
		String name = currentClass.getCanonicalName();
		TreeNode child = currentNode.getChild(name);
		
		if (child == null)
		{
			child = currentNode.createChild(name);
			if (currentClass.isPrimitive())
			{
				TreeNode tmp = TreeNode.getPrimitive(currentClass);
				child.nodeType = tmp.nodeType;
			}
			else
			{
				child.nodeType = currentClass.isInterface() ? TreeNode.Interface : TreeNode.Class;
				int classModifilers = currentClass.getModifiers();
				if (Modifier.isStatic(classModifilers))
				{
					child.nodeType |= TreeNode.Static;	
				}
			}
			child.arrayElement = getOrCreateNode(root, currentClass);
		}
			
		return child;
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
	    		if (!m.getName().equals(myMethod.getName())){
	    			continue;
	    		}

	    		Class<?>[] mp = m.getParameterTypes();
	    		if (mp.length != myMethodParams.length){
	    			continue;
	    		}
	    		
	    		boolean same = true;
	    		for (int i=0; i<mp.length; i++)
	    		{
	    			if (!mp[i].equals(myMethodParams[i]))
	    			{
	    				same = false;
	    				break;
	    			}
	    		}
	    		if (same){
	    			return true;
	    		}
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
}
