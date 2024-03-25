# Course Section (CourseSections)
| Field Name | Type                            | Is Primary Key |
|------------|---------------------------------|----------------|
| dept       | Varchar (String)                | Yes            |
| code       | Varchar (String)                | Yes            |
| section    | Varchar (String)                | Yes            |
| name       | Varchar (String)                | No             |
| semester   | Varchar (String)                | Yes            | 
| credits    | Integer (0.5 = -1 in the table) | No             |
| quota      | Integer                         | No             |
| enrol      | Integer                         | No             |
| waiting    | Integer                         | No             |
# Course Timetable (Timetables)
| Field Name | Type             | Is Primary Key |
|------------|------------------|----------------|
| dept       | Varchar (String) | Yes            |
| code       | Varchar (String) | Yes            |
| section    | Varchar (String) | Yes            |
| semester   | Varchar (String) | Yes            | 
| time       | Varchar (String) | Yes            |
| room       | Varchar (String) | No             |
# Course Instructor (Instructors)
| Field Name | Type                   | Is Primary Key |
|------------|------------------------|----------------|
| dept       | Varchar (String)       | Yes            |
| code       | Varchar (String)       | Yes            |
| section    | Varchar (String)       | Yes            |
| semester   | Varchar (String)       | Yes            | 
| type       | Varchar (String, enum) | No             |
| name       | Varchar (String)       | Yes            |
# Course Information (CourseInformation)
| Field Name | Type             | Is Primary Key |
|------------|------------------|----------------|
| dept       | Varchar (String) | Yes            |
| code       | Varchar (String) | Yes            |
| desc       | Varchar (String) | No             |
| excl       | Varchar (String) | No             |
| prereq     | Varchar (String) | No             |
| coreq      | Varchar (String) | No             |
| attribute  | Varchar (String) | No             |
