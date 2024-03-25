package hk.ust.comp4321.parse;

import hk.ust.comp4321.api.CourseInstructor;
import org.jsoup.Jsoup;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.jooq.impl.SQLDataType.INTEGER;
import static org.jooq.impl.SQLDataType.VARCHAR;

public class Parser {
    private static DSLContext create;
    public static void main(String[] args) throws IOException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:course-data.db")) {
            create = DSL.using(conn, SQLDialect.SQLITE);
            create.execute("PRAGMA foreign_keys = TRUE");
            create.createTableIfNotExists("CourseSections")
                    .column(DSL.field("dept"), VARCHAR)
                    .column(DSL.field("code"), VARCHAR)
                    .column(DSL.field("section"), VARCHAR)
                    .column(DSL.field("name"), VARCHAR)
                    .column(DSL.field("semester"), VARCHAR)
                    .column(DSL.field("credits"), INTEGER)
                    .column(DSL.field("quota"), INTEGER)
                    .column(DSL.field("enrol"), INTEGER)
                    .column(DSL.field("waiting"), INTEGER)
                    .constraints(
                            DSL.primaryKey("dept", "code", "section", "semester"),
                            DSL.foreignKey("dept", "code").references("CourseInformation", "dept", "code")
                    )
                    .execute();
            create.createTableIfNotExists("Timetables")
                    .column(DSL.field("dept"), VARCHAR)
                    .column(DSL.field("code"), VARCHAR)
                    .column(DSL.field("section"), VARCHAR)
                    .column(DSL.field("semester"), VARCHAR)
                    .column(DSL.field("time"), VARCHAR)
                    .column(DSL.field("room"), VARCHAR)
                    .constraints(
                            DSL.primaryKey("dept", "code", "section", "time"),
                            DSL.foreignKey("dept", "code", "section", "semester")
                                    .references("CourseSections", "dept", "code", "section", "semester")
                    )
                    .execute();
            create.createTableIfNotExists("Instructors")
                    .column(DSL.field("dept"), VARCHAR)
                    .column(DSL.field("code"), VARCHAR)
                    .column(DSL.field("section"), VARCHAR)
                    .column(DSL.field("semester"), VARCHAR)
                    .column(DSL.field("type"), VARCHAR)
                    .column(DSL.field("name"), VARCHAR)
                    .constraints(
                            DSL.primaryKey("dept", "code", "section", "name"),
                            DSL.foreignKey("dept", "code", "section", "semester")
                                    .references("CourseSections", "dept", "code", "section", "semester")
                    )
                    .execute();
            create.createTableIfNotExists("CourseInformation")
                    .column(DSL.field("dept"), VARCHAR)
                    .column(DSL.field("code"), VARCHAR)
                    .column(DSL.field("desc"), VARCHAR)
                    .column(DSL.field("excl"), VARCHAR)
                    .column(DSL.field("prereq"), VARCHAR)
                    .column(DSL.field("coreq"), VARCHAR)
                    .column(DSL.field("attribute"), VARCHAR)
                    .constraints(
                            DSL.primaryKey("dept", "code")
                    )
                    .execute();

            parseWeb(Files.list(Path.of("data")).map(f -> {
                try {
                    return Files.readString(f);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).toList());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public static void parseWeb(List<String> deptHtml) {
        for (String s : deptHtml) {
            Document doc = Jsoup.parse(s);
            String sem = doc.getElementsByClass("term").get(0).children().get(1).text();
            for (Element course : doc.getElementsByClass("course")) {
                String courseName = course.getElementsByTag("h2").get(0).text();
                courseName = courseName.trim();
                String[] courseArr = courseName.split(" ");
                String dept = courseArr[0];
                String code = courseArr[1];
                String name = courseName.substring(courseName.indexOf("-") + 1, courseName.lastIndexOf("(")).trim();
                String unitString = courseName.substring(courseName.lastIndexOf("(") + 1, courseName.lastIndexOf(")"));
                unitString = unitString.replace("units", "")
                        .replace("unit", "").trim();
                // Use negative to represent 0.5
                if (unitString.contains(".")) {
                    unitString = "-1";
                }
                int units = Integer.parseInt(unitString);

                //System.out.println(courseName);
                Elements cInfo = course.getElementsByClass("courseinfo").get(0).getElementsByClass("courseattr")
                        .get(0).getElementsByClass("popupdetail").get(0).children().get(0).children().get(0).children();
                String desc = "", excl = "", prereq = "", coreq = "", attribute = "";
                for (Element tableElement : cInfo) {
                    String type = tableElement.getElementsByTag("th").get(0).text();
                    String content = tableElement.getElementsByTag("td").get(0).wholeText();
                    switch (type.toLowerCase()) {
                        case "description" -> desc = content;
                        case "exclusion" -> excl = content;
                        case "pre-requisite" -> prereq = content;
                        case "co-requisite" -> coreq = content;
                        case "attribute" -> attribute = content;
                    }
                }
                create.insertInto(DSL.table("CourseInformation"))
                        .values(dept, code, desc, excl, prereq, coreq, attribute)
                        .onDuplicateKeyIgnore()
                        .execute();

                String sectionName = "";
                Elements sections = course.getElementsByTag("tr");
                sections.removeIf(e -> !e.hasClass("sectodd") && !e.hasClass("secteven"));
                for (int i = 0; i < sections.size(); i++) { // header removed by filter
                    Element section = sections.get(i);
                    Elements info = section.getElementsByTag("td");
                    int offset = 0;
                    if (section.hasClass("newsect")) {
                        sectionName = info.get(0).text().split(" ")[0];
                        offset++;
                    }
                    String time = info.get(offset++).wholeText();
                    String room = info.get(offset++).text();
                    String instructors = info.get(offset++).wholeText();
                    String ta = info.get(offset++).wholeText();
                    if (section.hasClass("newsect")) {
                        Element quotaElement = info.get(offset++);
                        quotaElement.getElementsByTag("div").remove();
                        int quota = Integer.parseInt(quotaElement.text());
                        int enrol = Integer.parseInt(info.get(offset++).text());
                        offset++;
                        int wait = Integer.parseInt(info.get(offset).text());
                        create.insertInto(DSL.table("CourseSections"))
                                .values(dept, code, sectionName, name, sem, units, quota, enrol, wait)
                                .onDuplicateKeyIgnore()
                                .execute();
                    }
                    String finalSectionName = sectionName;
                    Arrays.stream(instructors.split("\n"))
                            .filter(str -> !str.isBlank())
                            .forEach(instr -> create.insertInto(DSL.table("Instructors"))
                                    .values(dept, code, finalSectionName, sem, CourseInstructor.Type.INSTRUCTOR.toString(), instr)
                                    .onDuplicateKeyIgnore()
                                    .execute()
                            );
                    Arrays.stream(ta.split("\n"))
                            .filter(str -> !str.isBlank())
                            .forEach(instr -> create.insertInto(DSL.table("Instructors"))
                                    .values(dept, code, finalSectionName, sem, CourseInstructor.Type.TA.toString(), instr)
                                    .onDuplicateKeyIgnore()
                                    .execute()
                            );
                    create.insertInto(DSL.table("Timetables"))
                            .values(dept, code, finalSectionName, sem, time, trimOccupancy(room))
                            .onDuplicateKeyIgnore()
                            .execute();
                }
            }
        }
    }
    private static String trimOccupancy(String input) {
        int idx = input.lastIndexOf('(');
        if (idx != -1) {
            return input.substring(0, idx).trim();
        } else {
            return input.trim();
        }
    }
}
