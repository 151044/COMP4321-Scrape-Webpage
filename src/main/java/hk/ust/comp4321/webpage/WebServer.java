package hk.ust.comp4321.webpage;

import hk.ust.comp4321.api.CourseInformation;
import hk.ust.comp4321.api.CourseInstructor;
import hk.ust.comp4321.api.CourseSection;
import hk.ust.comp4321.api.SectionTimetable;
import io.javalin.Javalin;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jsoup.nodes.Entities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WebServer {
    private static DSLContext create;
    private static final Pattern COURSE_CODE = Pattern.compile("[A-Z]{4} \\d{4}[A-Z]?");
    public static void main(String[] args) throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:course-data.db");
            create = DSL.using(conn, SQLDialect.SQLITE);
            Javalin app = Javalin.create()
                    .get("/courses/course.css", ctx -> ctx.contentType("text/css").result(courseCss()))
                    .get("/room/room.css", ctx -> ctx.contentType("text/css").result(roomCss()))
                    .get("/courses/<dept>/<course>", ctx -> {
                        String dept = ctx.pathParam("dept");
                        String course = ctx.pathParam("course");
                        ctx.html(courseWebpage(dept, course));
                    })
                    .get("/courses/<dept>/", ctx -> {
                        String dept = ctx.pathParam("dept");
                        ctx.html(deptWebpage(dept));
                    })
                    .get("/instructor/<name>", ctx -> {
                        String name = ctx.pathParam("name");
                        ctx.html(instructorWebpage(name));
                    })
                    .get("/room/<room>", ctx -> {
                       String room = ctx.pathParam("room");
                       ctx.html(roomWebpage(room));
                    })
                    .error(404, ctx -> ctx.html("Sorry, there is no such webpage!"))
                    .start(7070);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    conn.close();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }));
    }
    private static String courseWebpage(String dept, String code) {
        List<CourseSection> sections = create.select()
                .from(DSL.table("CourseSections"))
                .where(DSL.condition("dept = '" + dept +
                        "'").and("code = '" + code + "'"))
                .fetch().stream().map(record -> new CourseSection(
                        record.get(0, String.class),
                        record.get(1, String.class),
                        record.get(2, String.class),
                        record.get(3, String.class),
                        record.get(4, String.class),
                        record.get(5, Integer.class),
                        record.get(6, Integer.class),
                        record.get(7, Integer.class),
                        record.get(8, Integer.class)
                )).toList();
        if (sections.isEmpty()) {
            return "Sorry, there is no such course!";
        }
        List<CourseInformation> infoQuery = create.select()
                .from(DSL.table("CourseInformation"))
                .where(DSL.condition("dept = '" + dept + "'").and("code = '" + code + "'"))
                .fetch().stream().map(record -> new CourseInformation(
                        record.get(0, String.class),
                        record.get(1, String.class),
                        record.get(2, String.class),
                        record.get(3, String.class),
                        record.get(4, String.class),
                        record.get(5, String.class),
                        record.get(6, String.class)
                )).toList();
        if (infoQuery.size() > 1) {
            System.err.println("Warning: Duplicate course information in table: " + dept + " " + code);
        } else if (infoQuery.isEmpty()) {
            throw new IllegalArgumentException("No such course " + dept + " " + code + " found in the database");
        }
        CourseInformation courseInfo = infoQuery.get(0);
        String infoFormat = "<p><b>%s:</b> <br>%s";
        String info = infoFormat.formatted("Description", parseAndUpdate(courseInfo.desc(), "../../")) +
                infoFormat.formatted("Exclusion", parseAndUpdate(courseInfo.excl(), "../../")) +
                infoFormat.formatted("Pre-Requisite", parseAndUpdate(courseInfo.prereq(), "../../")) +
                infoFormat.formatted("Co-Requisite", parseAndUpdate(courseInfo.coreq(), "../../")) +
                infoFormat.formatted("Attributes", parseAndUpdate(courseInfo.attribute(), "../../"));
        CourseSection first = sections.get(0);
        String headingString = first.dept() + " " + first.code() + " - " + first.name() + " (" +
                format(first.credits() < 0 ? 0.5 : first.credits()) + " unit"
                + (first.credits() == 1 ? "" : "s") + ")";

        List<SectionTimetable> timetables = create.select()
                .from(DSL.table("Timetables"))
                .where(DSL.condition(DSL.condition("dept = '" + dept + "'").and("code = '" + code + "'")))
                .fetch().stream().map(r -> new SectionTimetable(
                        r.get(0, String.class),
                        r.get(1, String.class),
                        r.get(2, String.class),
                        r.get(3, String.class),
                        r.get(4, String.class),
                        r.get(5, String.class)
                )).toList();
        List<String> semesters = timetables.stream().map(SectionTimetable::semester).distinct().toList();

        StringBuilder semBuilder = new StringBuilder();
        for (String sem : semesters) {
            semBuilder.append("<p>Sections for <b>").append(sem).append("</b>:");
            semBuilder.append("<table><tr><th>Section</th><th>Time</th><th>Room</th><th>Instructor</th><th>TAs</th></tr>");
            timetables.stream().filter(s -> s.semester().equals(sem)).map(s -> "<tr><td>" + s.section() + "</td><td>"
                + s.time() + "</td><td><a href=../../../room/" + s.room().replace(" ", "_")
                    + ">" + s.room() + "</a></td>" + getInstructorTa(s) + "</tr>").forEachOrdered(semBuilder::append);
            semBuilder.append("</table></p>");
        }
        String template =  """
                <!DOCTYPE html>
                <html>
                                
                <base href=/courses/%s/%s/>
                
                <link href="../../course.css" rel="stylesheet" />
                
                <head>
                  <title>%s %s - %s</title>
                </head>
                
                <body>
                <h1>%s</h1>
                
                %s
                
                %s
                <p><a href=../>Back to Department</a>
                </body>
                """;
        return template.formatted(dept, code, dept, code, first.name(), headingString, info, semBuilder.toString());
    }
    private static String deptWebpage(String dept) {
        String courses = create.select(DSL.field("code"), DSL.field("name"), DSL.field("credits"))
                .from(DSL.table("CourseSections"))
                .where(DSL.condition("dept = '" + dept + "'"))
                .fetch()
                .stream()
                .map(record -> "<a href=" + record.get(0, String.class) + ">" + dept + " " + record.get(0, String.class)
                        + " - " + record.get(1, String.class) + " (" +
                        format(record.get(2, Integer.class) < 0 ? 0.5 : record.get(2, Integer.class)) + " unit" +
                        (record.get(2, Integer.class) == 1 ? "" : "s") + ") </a><br>")
                .distinct()
                .collect(Collectors.joining("\n"));
        if (courses.isEmpty()) {
            return "This prefix does not exist!";
        }
        String template = """
                <!DOCTYPE html>
                <html>
                <base href="/courses/%s/">
                <head>
                  <title>%s Courses</title>
                </head>
                
                <h1>Courses for %s:</h1>
                %s
                
                <body>
                """;
        return template.formatted(dept, dept, dept, courses);
    }
    private static String instructorWebpage(String name) {
        name = name.replace("_", " ");
        List<CourseInstructor> instr = create.select().from(DSL.table("Instructors"))
                .where(DSL.condition("name = '" + name + "'"))
                .fetch().map(r -> new CourseInstructor(
                        r.get(0, String.class),
                        r.get(1, String.class),
                        r.get(2, String.class),
                        r.get(3, String.class),
                        CourseInstructor.Type.valueOf(r.get(4, String.class)),
                        r.get(5, String.class)
                ));
        if (instr.isEmpty()) {
            return "No such instructor!";
        }
        Map<String, List<CourseInstructor>> partitioned = new HashMap<>();
        instr.forEach(c -> partitioned.merge(c.semester(), new ArrayList<>(List.of(c)), (a, b) -> {
            a.addAll(b);
            return a;
        }));
        String courseFormat = "%s %s (%s) as %s";
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<CourseInstructor>> entry : partitioned.entrySet()) {
            builder.append("<p>Courses for ").append(entry.getKey()).append(":<br><ul>");
            for (CourseInstructor instruct : entry.getValue()) {
                builder.append("<li>").append(courseFormat.formatted(instruct.dept(), instruct.code(), instruct.section(), instruct.type()))
                        .append("</li>");
            }
            builder.append("</ul>");
        }

        String formatted = """
                <!DOCTYPE html>
                <html>
                <base href="/instructor/%s/">
                <head>
                  <title>%s</title>
                </head>
                
                <h1>Courses taught by %s:</h1>
                %s
                
                <body>
                """;
        return formatted.formatted(name, name, name, addCourseLinks(builder.toString(), "../../courses/"));
    }

    private static String roomWebpage(String room) {
        room = room.replace("_", " ");
        Map<String, List<SectionTimetable>> timetables = create.select()
                .from(DSL.table("Timetables"))
                        .where(DSL.condition(DSL.field("room").eq(room)))
                .fetch().stream()
                .map(r -> new SectionTimetable(
                        r.get(0, String.class),
                        r.get(1, String.class),
                        r.get(2, String.class),
                        r.get(3, String.class),
                        r.get(4, String.class),
                        r.get(5, String.class)
                )).collect(Collectors.groupingBy(SectionTimetable::semester));
        if (timetables.isEmpty()) {
            return "Sorry, there is no such room!";
        }
        StringBuilder builder = new StringBuilder();
        timetables.forEach((sem, timetable) -> {
            builder.append("<p>Timetable for semester <b>").append(sem).append("</b>:</p>");
            builder.append("<table><tr><th>Time</th><th>Course</th><th>Section</th></tr>");
            timetable.stream().sorted(Comparator.comparing(SectionTimetable::time)).forEachOrdered(s -> {
                builder.append("<tr><td>").append(s.time()).append("</td><td><a href=../../courses/").append(s.dept())
                        .append("/").append(s.code()).append(">").append(s.dept()).append(" ").append(s.code()).append("</a></td><td>")
                        .append(s.section()).append("</td></tr>");
            });
            builder.append("</table><br>");
        });

        String formatted = """
                <!DOCTYPE html>
                <html>
                <base href="/room/%s/">
                
                <link href="../room.css" rel="stylesheet" />
                
                <head>
                  <title>%s</title>
                </head>
                
                <h1>Timetable for %s:</h1>
                %s
                
                <body>
                """;
        return formatted.formatted(room, room, room, builder.toString());
    }

    private static String courseCss() {
        return """
                table {
                  table-layout: fixed;
                  width: 100%;
                  border-collapse: collapse;
                  border: 3px solid #b3d1da;
                }
                                
                thead th:nth-child(1) {
                  width: 10%;
                }
                                
                thead th:nth-child(2) {
                  width: 20%;
                }
                                
                thead th:nth-child(3) {
                  width: 20%;
                }
                                
                thead th:nth-child(4) {
                  width: 25%;
                }
                
                thead th:nth-child(5) {
                  width: 25%;
                }
                                
                th,
                td {
                  padding: 20px;
                }
                
                
                tbody tr:nth-child(odd) {
                  background-color: #ffffff;
                }
                                
                tbody tr:nth-child(even) {
                  background-color: #e1eef2;
                }
                
                tbody td {
                  text-align: center;
                }
                """;
    }

    private static String roomCss() {
        return """
                table {
                  table-layout: fixed;
                  width: 100%;
                  border-collapse: collapse;
                  border: 3px solid #b3d1da;
                }
                                
                thead th:nth-child(1) {
                  width: 50%;
                }
                                
                thead th:nth-child(2) {
                  width: 25%;
                }
                                
                thead th:nth-child(3) {
                  width: 25%;
                }
                                
                th,
                td {
                  padding: 20px;
                }
                
                
                tbody tr:nth-child(odd) {
                  background-color: #ffffff;
                }
                                
                tbody tr:nth-child(even) {
                  background-color: #e1eef2;
                }
                
                tbody td {
                  text-align: center;
                }
                """;
    }

    private static String parseAndUpdate(String str, String relativeTo) {
        if (str.isEmpty()) {
            return "N/A";
        } else {
            String escaped = Entities.escape(str).replace("?", "&#63;").replace("’", "&#39;")
                    .replace("‐", "&#45;");
            return addCourseLinks(escaped, relativeTo);
        }
    }
    private static String addCourseLinks(String str, String relativeTo) {
        return COURSE_CODE.matcher(str).replaceAll(mr -> {
            String courseCode = mr.group();
            String[] courseInfo = courseCode.split(" ");
            return "<a href=" + relativeTo + courseInfo[0] + "/" + courseInfo[1] + ">" + courseCode + "</a>";
        });
    }
    private static String format(double d) {
        return NumberFormat.getInstance().format(d);
    }
    private static String getInstructorTa(SectionTimetable tt) {
        StringBuilder builder = new StringBuilder("<td>");
        Map<CourseInstructor.Type, List<CourseInstructor>> instrByType = create.select().from(DSL.table("Instructors"))
                .where(DSL.field(DSL.name("dept")).eq(tt.dept()).and(
                        DSL.field(DSL.name("code")).eq(tt.code()).and(
                                DSL.field(DSL.name("section")).eq(tt.section()).and(
                                        DSL.field(DSL.name("semester")).eq(tt.semester())
                                )
                        )
                )).fetch().stream().map(r -> new CourseInstructor(r.get(0, String.class),
                        r.get(1, String.class),
                        r.get(2, String.class),
                        r.get(3, String.class),
                        CourseInstructor.Type.valueOf(r.get(4, String.class)),
                        r.get(5, String.class)
                )).collect(Collectors.groupingBy(CourseInstructor::type));
        builder.append("<ul><li>");
        String instructors = instrByType.getOrDefault(CourseInstructor.Type.INSTRUCTOR, List.of()).stream().map(CourseInstructor::name)
                .map(s -> "<a href=../../../instructor/" + s.replace(" ", "_") + ">" + s + "</a>" )
                .collect(Collectors.joining("</li><li>"));
        if (instructors.isEmpty()) {
            builder.append("TBA");
        } else {
            builder.append(instructors);
        }
        builder.append("</li></ul></td><td>");
        String tas = instrByType.getOrDefault(CourseInstructor.Type.TA, List.of()).stream().map(CourseInstructor::name)
                .map(s -> "<a href=../../../instructor/" + s.replace(" ", "_") + ">" + s + "</a>" )
                .collect(Collectors.joining("</li><li>"));
        if (tas.isEmpty()) {
            builder.append("TBA");
        } else {
            builder.append(tas);
        }
        return builder.append("</li></ul></td><td>").toString();
    }
}
