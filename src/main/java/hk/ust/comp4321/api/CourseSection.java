package hk.ust.comp4321.api;

public record CourseSection(String dept, String code, String section, String name, String semester,
                            int credits, int quota, int enrol, int waiting) {
}
