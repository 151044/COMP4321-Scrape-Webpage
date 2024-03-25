package hk.ust.comp4321.api;

public record CourseInstructor(String dept, String code, String section, String semester, Type type, String name) {
    public enum Type {
        INSTRUCTOR {
            @Override
            public String toString() {
                return "Instructor";
            }
        }, TA
    }
}