package com.telerik.bindings;

public class Generator {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		String dirName = "jars";
		if(args != null && args.length > 0){
			dirName = args[0];
		}
		
		TreeNode root = MetadataBuilder.build(dirName);
		MetadataWriter.writeTree(root);
		
//		String[] jars1 = { 
//				"android17.jar", "ion-1.2.4.jar", 
//				"androidasync-1.2.4.jar", 
//				"support-v4-r13.jar", 
//				"nativescript.jar",
//				"Common.jar",
//				"Primitives.jar",
//				"Chart.jar"
//				};
//				
//		JarLister.startGenerateMetadata(args, jars1);
		
//		String[] jars2 = { "android17.jar", "ion-1.2.4.jar", "androidasync-1.2.4.jar", "support-v4-r13.jar" };
		
//		JarLister.startGenerateBindings(args, jars2);
		
	}
}
