package translation;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

import utils.FileIO;

public class Translator {
	public static final HashSet<String> separators = new HashSet<>();

	static {
		separators.add("and");
		separators.add("or");
		separators.add("&&");
		separators.add("||");
	}

	public static void splitClauses(String input, String intermediate) {
		String content = FileIO.readStringFromFile(input);
		StringBuilder clauses = new StringBuilder(), connectors = new StringBuilder();
		int count = 0;
		Scanner sc = new Scanner(content);
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			String[] tokens = split(line, separators);
			for (String token : tokens) {
				if (separators.contains(token)) {
					connectors.append(token + " ");
				} else {
					clauses.append(token + "\n");
					count++;
					connectors.append(count + " ");
				}
			}
			connectors.append("\n");
		}
		sc.close();
		FileIO.writeStringToFile(clauses.toString(), intermediate + "/clauses.txt");
		FileIO.writeStringToFile(connectors.toString(), intermediate + "/connectors.txt");
	}
	
	public static void construct(String intermediate, String output) {
		HashMap<String, String> conditions = new HashMap<>();
		String content = FileIO.readStringFromFile(intermediate + "/conditions.txt");
		Scanner sc = new Scanner(content);
		int i = 0;
		while (sc.hasNextLine()) {
			i++;
			conditions.put(i + "", sc.nextLine().trim());
		}
		sc.close();

		StringBuilder sb = new StringBuilder();
		content = FileIO.readStringFromFile(intermediate + "/connectors.txt");
		sc = new Scanner(content);
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			String[] tokens = line.split(" ");
			for (String token : tokens) {
				token = token.trim();
				String condition = conditions.get(token);
				if (condition == null) {
					if (token.equals("or"))
						sb.append("|| ");
					else if (token.equals("and"))
						sb.append("&& ");
					else if (token.equals("||"))
						sb.append("or ");
					else if (token.equals("&&"))
						sb.append("and ");
					else
						sb.append(token + " ");
				} else
					sb.append(condition + " ");
			}
			sb.append("\n");
		}
		sc.close();
		FileIO.writeStringToFile(sb.toString(), output);
	}

	public static void translate(String input, String inter, String output) {
		splitClauses(input, inter);
		File fClauses = new File(inter + "/clauses.txt");
		while (true) {
			File fConditions = new File(inter + "/conditions.txt");
			if (fConditions.exists() && fConditions.lastModified() > fClauses.lastModified())
				break;
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		construct(inter, output);
	}

	private static String[] split(String line, HashSet<String> separators) {
		ArrayList<String> tokens = new ArrayList<>();
		line = line.replace(" than or ", " than_or ");
		String[] parts = line.split(" ");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			String token = parts[i].trim();
			if (separators.contains(token)) {
				tokens.add(sb.toString().trim());
				sb = new StringBuilder();
				tokens.add(token);
			} else {
				sb.append(token.replace("than_or", "than or") + " ");
			}
		}
		tokens.add(sb.toString().trim());
		return tokens.toArray(new String[]{});
	}

	public static void main(String[] args) {
		translate("T:/spectrans/specs.txt", "T:/spectrans", "T:/spectrans/code.txt");
	}
}
