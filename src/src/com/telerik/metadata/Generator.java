package com.telerik.metadata;

public class Generator {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		String dirName = "../jars";
		if(args != null && args.length > 0){
			dirName = args[0];
		}
		
		String outName = "bin";
		if(args != null && args.length > 1){
			outName = args[1];
		}
		
		TreeNode root = Builder.build(dirName);
		Writer.writeTree(outName, root);
	}
}
