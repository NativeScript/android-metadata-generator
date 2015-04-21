package com.telerik.metadata;

import java.io.File;
import java.io.FileOutputStream;

public class Generator {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception
	{
		String dirName = "../jars";
		if (args != null && args.length > 0)
		{
			dirName = args[0];
		}

		String outName = "bin";
		if (args != null && args.length > 1)
		{
			outName = args[1];
		}

		TreeNode root = Builder.build(dirName);
		
		FileOutputStream ovs = new FileOutputStream(new File(outName, "treeValueStream.dat"));
		FileStreamWriter outValueStream = new FileStreamWriter(ovs); 

		FileOutputStream ons = new FileOutputStream(new File(outName, "treeNodeStream.dat"));
		FileStreamWriter outNodeStream = new FileStreamWriter(ons);

		FileOutputStream oss = new FileOutputStream(new File(outName, "treeStringsStream.dat"));
		FileStreamWriter outStringsStream = new FileStreamWriter(oss);

		new Writer(outNodeStream, outValueStream, outStringsStream).writeTree(root);
	}
}
