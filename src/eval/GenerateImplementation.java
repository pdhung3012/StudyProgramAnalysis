package eval;

import parsing.ParallelCorpusParser;

public class GenerateImplementation {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String fop = "C:\\Users\\pdhung\\Desktop\\hungData\\research\\ImportantProjects\\SpecMiningProject\\SummerWork2017\\apache\\";
		ParallelCorpusParser pcp = new ParallelCorpusParser(fop + "list.txt");
		pcp.generateParallelCorpus(fop, true, true, false);
	}

}
