import java.io.Serializable;
import java.util.ArrayList;

class Course implements Serializable {
    String status;
    String CRN;
    String subjectAcronym;
    String subjectName;
    String subjectNumber;
    String bulletinLink;
    String sectionNumber;
    String courseName;
    int credit;
    ArrayList <String> instructor;
    ArrayList <ClassDay> classDays;
    String start;
    String end;
    String comment;
    String oldCourseNumber;
    String findBooksLink;
    ArrayList <Course> xList;
    ArrayList <Course> Linked;
    ArrayList <CourseAttribute> courseAttributes;
    String fee;

    String bulletinDescription;

}

class ClassDay implements Serializable {
    // legacy days, migrated to mon, tues, etc fields
//    String days;
    Boolean monday = false;
    Boolean tuesday = false;
    Boolean wednesday = false;
    Boolean thursday = false;
    Boolean friday = false;
    Boolean saturday = false;
    Boolean sunday = false;
    String location;
    String startTime;
    String endTime;
}

class Department {
    String name;
    String acronym;
}

class CourseAttribute implements Serializable {
    String name;
    String acronym;
}