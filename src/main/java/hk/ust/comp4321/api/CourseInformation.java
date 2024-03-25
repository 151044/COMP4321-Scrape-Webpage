package hk.ust.comp4321.api;

public record CourseInformation(String dept, String code, String desc, String excl,
                                String prereq, String coreq, String attribute) {}