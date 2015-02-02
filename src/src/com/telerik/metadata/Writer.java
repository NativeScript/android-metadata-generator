package com.telerik.metadata;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;

import com.telerik.metadata.TreeNode.FieldInfo;
import com.telerik.metadata.TreeNode.MethodInfo;

public class Writer {
	private static byte[] writeUniqueName_lenBuff = new byte[2];
	private static byte[] writeInt_buff = new byte[4];
	private static byte[] writeTreeNodeId_buff = new byte[2];
	private static byte[] writeLength_lenBuff = new byte[2];
	private static byte[] writeModifierFinal_buff = new byte[1];

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
	
<<<<<<< HEAD
<<<<<<< HEAD
=======
	private static void writeFinalModifier(FieldInfo fi, FileOutputStream out) throws Exception 
	{
		writeModifierFinal_buff[0] = fi.isFinalType ? TreeNode.Final : 0;
		out.write(writeModifierFinal_buff);
	}
	
>>>>>>> a36aa62... fixed bug in metadata generation
=======
	private static void writeFinalModifier(FieldInfo fi, FileOutputStream out) throws Exception 
	{
		if(fi.isFinalType)
		{
			writeModifierFinal_buff[0] = (byte) (TreeNode.Final & 0xFF);
			out.write(writeModifierFinal_buff);
		}
	}
	
>>>>>>> c4b2b47823c956332359a3d899ac9d5eff8c06b9
	private static int writeLength(int length, FileOutputStream out) throws Exception
	{
		writeLength_lenBuff[0] = (byte) (length & 0xFF);
		writeLength_lenBuff[1] = (byte) ((length >> 8) & 0xFF);
		out.write(writeLength_lenBuff);
		
		return length;
	}
	
	public static void writeTree(TreeNode root) throws Exception
	{
		short curId = 0;

		ArrayDeque<TreeNode> d = new ArrayDeque<TreeNode>();
		
		FileOutputStream outStringsStream = new FileOutputStream("bin/treeStringsStream.dat");
		
		HashMap<String, Integer> uniqueStrings = new HashMap<String, Integer>();
		
		int commonInterfacePrefixPosition = writeUniqueName("com/tns/", uniqueStrings, outStringsStream);
		
		//this while loop fils the treeStringsStream.dat file with a sequence of the
		//length and name of all the nodes in the built tree + the primitive types used by method signatures
		//the "n" variable holds all the nodes -> n.offsetName is the initial position where you can read the (length/name) pair
		//n.offsetName is used to later find the node names in the treeStringsStream.dat file
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
						writeUniqueName(name, uniqueStrings, outStringsStream);
					}
				}
				for (int i=0; i<n.staticMethods.size(); i++)
				{
					name = n.staticMethods.get(i).name;
					if (!uniqueStrings.containsKey(name))
					{
						writeUniqueName(name, uniqueStrings, outStringsStream);
					}
				}
				for (int i=0; i<n.instanceFields.size(); i++)
				{
					name = n.instanceFields.get(i).name;
					if (!uniqueStrings.containsKey(name))
					{
						writeUniqueName(name, uniqueStrings, outStringsStream);
					}
				}
				for (int i=0; i<n.staticFields.size(); i++)
				{
					name = n.staticFields.get(i).name;
					if (!uniqueStrings.containsKey(name))
					{
						writeUniqueName(name, uniqueStrings, outStringsStream);
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
					outValueStream.write(1);
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
					int pos = uniqueStrings.get(fi.name).intValue(); //get start position of the name
					writeInt(pos, outValueStream); // write start position of the name of the variable
					writeTreeNodeId(fi.valueType, outValueStream); //pointer to the value type of the variable
					writeFinalModifier(fi, outValueStream);
				}

				len = writeLength(n.staticFields.size(), outValueStream);
				for (int i=0; i<len; i++)
				{
					FieldInfo fi = n.staticFields.get(i);
					int pos = uniqueStrings.get(fi.name).intValue();
					writeInt(pos, outValueStream);
					writeTreeNodeId(fi.valueType, outValueStream);
					writeFinalModifier(fi, outValueStream);
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
