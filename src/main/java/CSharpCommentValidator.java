import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class CSharpCommentValidator {
    public static void main(String[] args) throws IOException {
        String magic = "(?<accessability>public|private|internal|protected)\\s(async\\s)?((static|virtual|abstract|override|new)\\s)?(async\\s)?(?<returnType>[a-zA-Z0-9<>]*)\\s(?<methodName>[A-Za-z_0-9<>, ]*)\\((?<arguments>[A-Za-z_0-9\\[\\]\\<\\>, ]*)\\)";
        Pattern pattern = Pattern.compile(magic);
        List<String> files = new ArrayList<>();
        List<String> folders = Arrays.asList(args);
        List<String> issues = new ArrayList<>();
        for (String folder : folders) {
            try (Stream<Path> walk = Files.walk(Paths.get(folder))) {
                List<String> result = walk
                    .filter(p -> !Files.isDirectory(p))
                    .map(Path::toString)
                    .filter(f -> f.endsWith("cs")).toList();
                files.addAll(result);
            }
        }
        for (String fileName : files) {
            File file = new File(fileName);
            List<String> lines = Files.readAllLines(file.toPath());
            for (int i = 0; i < lines.size(); i++) {
                issues.addAll(findConstructorIssues(lines, i, pattern, file));
                issues.addAll(findInlineIssues(lines, i, file));
            }
        }
        if(issues.size()>0) {
            issues.stream().forEach(System.out::println);
            System.exit(1);
        }
    }

    public static List<String> findConstructorIssues(List<String> lines, int i, Pattern pattern, File file){
        List<String> issues = new ArrayList<>();
        String line = lines.get(i);
        if(line.contains("record")){
            return issues;
        }
        Matcher matcher = pattern.matcher(line.strip());
        if(matcher.matches()) {
            if(checkExempt(lines, i - 1)) {
                return new ArrayList<>();
            }
            String methodName = matcher.group("methodName").strip();
            boolean returnvalue = !matcher.group("returnType").equals("void");
            String[] arguments = null;
            if(!matcher.group("arguments").equals("")){
                arguments = matcher.group("arguments").split(", ");
            }
            int lineNumber = i - 1;
            List<String> comments = new ArrayList<>();
            while(lines.get(lineNumber).contains("///")){
                comments.add(lines.get(lineNumber).replace("///", ""));
                lineNumber--;
            }
            Collections.reverse(comments);
            String commentsString = String.join("\n", comments);
            issues.addAll(findSummaryIssues(commentsString, methodName, i, file));
            if(returnvalue) {
                issues.addAll(findReturnIssues(commentsString, methodName, i, file));
            }
            if(arguments != null) {
                List<String> argNames = Arrays.stream(arguments).map(x -> x.split(" ")[x.split(" ").length-1]).toList();
                List<String> params = findParams(comments.stream().map(String::strip).toList());
                issues.addAll(findParamIssues(argNames, params, methodName, i, file));
            }
        }
        return issues;
    }

    public static List<String> findSummaryIssues(String commentsString, String methodName, int i, File file){
        List<String> issues = new ArrayList<>();
        if(!matches(commentsString, "(<summary>\\s* [A-Z])") || !matches(commentsString, "(\\.\\s*</summary>)")) {
            issues.add(ConsoleColors.YELLOW + "Method " + methodName + ConsoleColors.RESET + " does not have a summary");
            issues.add("File: " + file.getParentFile().getPath() + File.separator + ConsoleColors.BLUE + file.getName() + ":" + ConsoleColors.GREEN + (i + 1) + ConsoleColors.RESET);
            issues.add("");
        }
        return issues;
    }

    public static List<String> findReturnIssues(String commentsString, String methodName, int i, File file){
        List<String> issues = new ArrayList<>();
        if(!matches(commentsString, "(<returns> [A-Z]).*(\\. </returns>)")){
            issues.add(ConsoleColors.YELLOW + "Method " + methodName + ConsoleColors.RESET + " has a return type, but no return comment");
            issues.add("File: " + file.getParentFile().getPath() + File.separator + ConsoleColors.BLUE + file.getName() + ":" + ConsoleColors.GREEN + (i + 1) + ConsoleColors.RESET);
            issues.add("");
        }
        return issues;
    }

    public static List<String> findParams(List<String> lines){
        List<String> params = new ArrayList<>();
        Pattern pattern = Pattern.compile("<param name=\"(?<argName>[^\"]*)\"> [A-Z].*\\. </param>");
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);

            if(matcher.matches()){
                params.add(matcher.group("argName").strip());
            }
        }
        return params;
    }

    public static List<String> findParamIssues(List<String> expected, List<String> found, String methodName, int i, File file){
        List<String> issues = new ArrayList<>();
        List<String> expectedButNotFound = new ArrayList<>(expected);
        expectedButNotFound.removeAll(found);
        for (String s : expectedButNotFound) {
            issues.add(ConsoleColors.YELLOW + "Method " + methodName + ConsoleColors.RESET + " does not have a parameter comment for parameter " + s);
            issues.add("File: " + file.getParentFile().getPath() + File.separator + ConsoleColors.BLUE + file.getName() + ":" + ConsoleColors.GREEN + (i + 1) + ConsoleColors.RESET);
            issues.add("");
        }
        List<String> foundButNotExpected = new ArrayList<>(found);
        foundButNotExpected.removeAll(expected);
        for (String s : foundButNotExpected) {
            issues.add(ConsoleColors.YELLOW + "Method " + methodName + ConsoleColors.RESET +
                " has a redundant comment for parameter " + s);
            issues.add(
                "File: " + file.getParentFile().getPath() + File.separator + ConsoleColors.BLUE +
                    file.getName() + ":" + ConsoleColors.GREEN + (i + 1) + ConsoleColors.RESET);
            issues.add("");
        }
        return issues;
    }

    public static List<String> findInlineIssues(List<String> lines, int i, File file){
        List<String> issues = new ArrayList<>();
        String line = lines.get(i);
        if(matches(line, "[^/]// ")){
            if(!checkExempt(lines, i)){
                if(!matches(line, "[^/]// [A-Z].*\\. ?$")){
                    issues.add(ConsoleColors.YELLOW + "File " + file.getName() + ConsoleColors.RESET + " does not have a proper comment at line " + (i + 1));
                    issues.add("File: " + file.getParentFile().getPath() + File.separator + ConsoleColors.BLUE + file.getName() + ":" + ConsoleColors.GREEN + (i + 1) + ConsoleColors.RESET);
                    issues.add("");
                }
            }
        }
        return issues;
    }

    public static boolean checkExempt(List<String> lines, int i) {
        while(lines.get(i).contains("// ")) {
            if(lines.get(i).contains("// <exempt>")){
                return true;
            }
            i--;
        }
        return false;
    }

    public static boolean matches(String input, String regex) {
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(input).find();
    }
}
