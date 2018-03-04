import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.rpc.Help;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.Select;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static com.sun.prism.impl.Disposer.cleanUp;

public class PullCourses {

    private static Firestore db;
    private static ArrayList<String> htmlPages = new ArrayList<>();
    private static ArrayList <Course> totalCourseData = new ArrayList<>();
    private static String lastRevisedTime;
    private static WebDriver driver;

    public static void main (String[] argv) {

        initFirestore();
        runSelenium();
//        loadDataFromFile();
        uploadData();
        cleanUp();

    }

    private static void initFirestore() {
        FirestoreOptions firestoreOptions =
                FirestoreOptions.getDefaultInstance().toBuilder()
                        .setProjectId("course-gnome")
                        .build();
        db = firestoreOptions.getService();
    }

    private static void runSelenium() {
        int startSubjectNumber = 1000;
        int endSubjectNumber = 1099;

        System.setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--headless");
        driver = new ChromeDriver(chromeOptions);

        long startClock = System.currentTimeMillis();

        while (endSubjectNumber<9999) {
            // go to home page
            driver.get("https://my.gwu.edu/mod/pws/coursesearch.cfm");
            System.out.println(startSubjectNumber + " - " + endSubjectNumber);

            // select term, enter subject number range, search
            new Select(driver.findElement(By.name("term"))).selectByVisibleText("Spring 2018");
            driver.findElement(By.name("courseNumSt")).clear();
            driver.findElement(By.name("courseNumSt")).sendKeys(String.valueOf(startSubjectNumber));
            driver.findElement(By.name("courseNumEn")).clear();
            driver.findElement(By.name("courseNumEn")).sendKeys(String.valueOf(endSubjectNumber));
            driver.findElement(By.name("Submit")).click();

            // add html
            String html = (driver.getPageSource());
            htmlPages.add(html);

            // increment range
            startSubjectNumber += 100;
            endSubjectNumber += 100;

            // check if there is a next page, if there isn't, go back to home
            int index = html.indexOf("nextPage()");
            if (index > 0) {
                // if there is, loop through until all pages are added
                while (index > 0) {
                    driver.findElement(By.linkText("Next Page >>")).click();
                    html = driver.getPageSource();
                    htmlPages.add(html);
                    index = html.indexOf("nextPage()");
                }
            }
        }
        System.out.println("SQL Runtime (millis): " + (System.currentTimeMillis() - startClock));
        System.out.println("SQL Runtime (sec): " + (System.currentTimeMillis() - startClock)/1000);
        System.out.println("SQL Runtime (min): " + (System.currentTimeMillis() - startClock)/1000/60);
        parseData();
        driver.close();
    }

    private static void parseData() {

        HashMap<String, String> departments = new HelperLists().getDepartments();

        int openIndex;
        int closeIndex;
        int tempIndex;
        String temp;
        boolean firstXList = true;
        boolean firstLinked = true;
        boolean isXList;
        boolean isLinked;

        int pageCount = htmlPages.size();

        openIndex = htmlPages.get(0).indexOf ("Revised");
        closeIndex = htmlPages.get(0).indexOf ("<", openIndex);
        lastRevisedTime = htmlPages.get(0).substring(openIndex+10, closeIndex);
        openIndex = 4000;

        for (int i = 0; i < pageCount; ++i) {

            while (true) {

                openIndex = htmlPages.get(i).indexOf("tr align=\"center\" class=\"tableRow", openIndex);
                if (openIndex == -1) {
                    break;
                }
                isXList = false;
                isLinked = false;
                Course course = new Course();
                closeIndex = htmlPages.get(i).indexOf("-",openIndex);
                closeIndex = htmlPages.get(i).indexOf("\"",closeIndex) -1;
                if (htmlPages.get(i).charAt(closeIndex)=='L') {
                    isLinked = true;
                    if (firstLinked) {
                        totalCourseData.get(totalCourseData.size()-1).Linked = new ArrayList<>();
                        totalCourseData.get(totalCourseData.size()-1).Linked.add(course);
                        firstLinked = false;
                    } else {
                        totalCourseData.get(totalCourseData.size()-1).Linked.add(course);
                    }
                } else if (htmlPages.get(i).charAt(closeIndex)=='D'){
                    isXList = true;
                    if (firstXList) {
                        totalCourseData.get(totalCourseData.size()-1).xList = new ArrayList<>();
                        totalCourseData.get(totalCourseData.size()-1).xList.add(course);
                        firstXList = false;
                    } else {
                        totalCourseData.get(totalCourseData.size()-1).xList.add(course);
                    }
                } else {
                    totalCourseData.add(course);
                    firstXList = true;
                    firstLinked = true;
                }

                openIndex = htmlPages.get(i).indexOf("<td>", openIndex) + 4;
                closeIndex = htmlPages.get(i).indexOf("</td>", openIndex);
                course.status = htmlPages.get(i).substring(openIndex, closeIndex);

                String tempStatus = course.status.substring(0,1);
                course.status = course.status.toLowerCase();
                tempStatus = tempStatus.concat(course.status.substring(1,course.status.length()));
                course.status = tempStatus;

                if (course.status.equals("Cancelled")) {
                    if (isXList) {
                        totalCourseData.get(totalCourseData.size() - 1).xList.remove(course);
                    } else if (isLinked) {
                        totalCourseData.get(totalCourseData.size() - 1).Linked.remove(course);
                    } else {
                        totalCourseData.remove(course);
                    }
                    continue;
                }

                openIndex = htmlPages.get(i).indexOf("<td>", openIndex) + 4;
                course.CRN = htmlPages.get(i).substring(openIndex, openIndex + 5);

                openIndex = htmlPages.get(i).indexOf("\t\t\t\t", openIndex) + 4;
                closeIndex = htmlPages.get(i).indexOf("\n", openIndex);
                course.subjectAcronym = htmlPages.get(i).substring(openIndex, closeIndex);
                course.subjectName = departments.get(course.subjectAcronym);
                if (course.subjectName == null) {
                    totalCourseData.remove(course);
                    continue;
                }

                openIndex = htmlPages.get(i).indexOf("http", openIndex);
                closeIndex = htmlPages.get(i).indexOf("target", openIndex) - 2;
                course.bulletinLink = htmlPages.get(i).substring(openIndex, closeIndex);

                driver.get(course.bulletinLink);
                String bulletinHTML = driver.getPageSource();

                course.bulletinDescription = driver.findElement(By.xpath("//*[@id=\"fssearchresults\"]/div/div/p")).getText();


                openIndex = htmlPages.get(i).indexOf("\t\t\t\t\t", openIndex) + 5;
                closeIndex = htmlPages.get(i).indexOf("\n", openIndex);
                course.subjectNumber = htmlPages.get(i).substring(openIndex, closeIndex);

                openIndex = htmlPages.get(i).indexOf("<td>", openIndex) + 4;
                closeIndex = htmlPages.get(i).indexOf("</td>", openIndex);
                course.sectionNumber = htmlPages.get(i).substring(openIndex, closeIndex);

                openIndex = htmlPages.get(i).indexOf("<td>", openIndex) + 4;
                closeIndex = htmlPages.get(i).indexOf("</td>", openIndex);
                course.courseName = htmlPages.get(i).substring(openIndex, closeIndex).replaceAll("\"", "?").replaceAll("&amp;", "&");

                openIndex = htmlPages.get(i).indexOf("<td>", openIndex) + 4;
                closeIndex = htmlPages.get(i).indexOf("</td>", openIndex);
                temp = (htmlPages.get(i).substring(openIndex, closeIndex)).trim();
                if (temp.charAt(0) == 'A') {
                    course.credit = 3;
                } else {
                    course.credit = temp.charAt(0) - 48;
                }

                openIndex = htmlPages.get(i).indexOf("<td>", openIndex) + 4;
                closeIndex = htmlPages.get(i).indexOf("</td>", openIndex);
                course.instructor = new ArrayList<>();
                tempIndex = htmlPages.get(i).indexOf(';', openIndex);
                if (tempIndex < (openIndex+40) && tempIndex > 0) {
                    while (tempIndex < (openIndex+40) && tempIndex > 0) {
                        course.instructor.add(htmlPages.get(i).substring(openIndex, tempIndex).trim());
                        openIndex = tempIndex + 1;
                        tempIndex = htmlPages.get(i).indexOf(';', openIndex);
                    }
                    course.instructor.add(htmlPages.get(i).substring(openIndex, closeIndex).trim());
                } else {
                    course.instructor.add(htmlPages.get(i).substring(openIndex, closeIndex).trim());
                }

                for (int k=0;k<course.instructor.size();++k) {
                    if (course.instructor.get(k).equals("")) {
                        course.instructor.set(k,"None listed");
                    }
                }

                ClassDay day = new ClassDay();
                course.classDays = new ArrayList<>();
                course.classDays.add(day);

                openIndex = htmlPages.get(i).indexOf("<td>", openIndex) + 4;

                if (htmlPages.get(i).substring(openIndex,openIndex+2).equals("<a")) {
                    openIndex = htmlPages.get(i).indexOf("blank", openIndex) + 7;
                    closeIndex = htmlPages.get(i).indexOf("</a>", openIndex);
                    day.location = htmlPages.get(i).substring(openIndex, closeIndex);
                    openIndex = closeIndex + 4;
                    closeIndex = htmlPages.get(i).indexOf("<", openIndex);
                    day.location = day.location.concat(htmlPages.get(i).substring(openIndex, closeIndex));
                } else {
                    closeIndex = htmlPages.get(i).indexOf("<", openIndex);
                    day.location = htmlPages.get(i).substring(openIndex, closeIndex);
                }
                if (htmlPages.get(i).charAt(closeIndex+6) == 'A') {
                    openIndex = htmlPages.get(i).indexOf("AND", openIndex) + 9;
                    while (true) {
                        ClassDay anotherDay = new ClassDay();
                        course.classDays.add(anotherDay);
                        if (htmlPages.get(i).charAt(openIndex) == '<') {
                            openIndex = htmlPages.get(i).indexOf("blank", openIndex) + 7;
                            closeIndex = htmlPages.get(i).indexOf("</a>", openIndex);
                            anotherDay.location = htmlPages.get(i).substring(openIndex, closeIndex);
                            openIndex = closeIndex + 4;
                            closeIndex = htmlPages.get(i).indexOf("<", openIndex);
                            anotherDay.location = anotherDay.location.concat(" " + htmlPages.get(i).substring(openIndex, closeIndex));
                        } else {
                            closeIndex = htmlPages.get(i).indexOf("<", openIndex);
                            anotherDay.location = htmlPages.get(i).substring(openIndex, closeIndex);
                        }
                        if (htmlPages.get(i).charAt(closeIndex+6) != 'A') {
                            break;
                        }
                        openIndex = htmlPages.get(i).indexOf("AND", openIndex) + 9;
                    }
                }

                openIndex = htmlPages.get(i).indexOf("<td>", openIndex) + 4;
                closeIndex = htmlPages.get(i).indexOf("<", openIndex);

                int j = 0;
                int temporary;
                String days;

                if (openIndex != closeIndex) {

                    while (true) {
                        days = htmlPages.get(i).substring(openIndex, closeIndex);
                        openIndex = htmlPages.get(i).indexOf(">", openIndex) + 1;
                        closeIndex = htmlPages.get(i).indexOf(":", openIndex);
                        temporary = Integer.valueOf(htmlPages.get(i).substring(openIndex, closeIndex));
                        if (htmlPages.get(i).charAt(closeIndex + 3) == 'P') {
                            if (!(htmlPages.get(i).substring(closeIndex-2,closeIndex).equals("12"))) {
                                temporary += 12;
                            }
                        }
                        course.classDays.get(j).startTime = String.valueOf(temporary);
                        openIndex = closeIndex + 1;
                        closeIndex = htmlPages.get(i).indexOf("M", openIndex) - 1;
                        course.classDays.get(j).startTime = course.classDays.get(j).startTime.concat(":" + htmlPages.get(i).substring(openIndex, closeIndex));
                        closeIndex = htmlPages.get(i).indexOf(":", closeIndex);
                        openIndex = closeIndex - 2;
                        temporary = Integer.valueOf(htmlPages.get(i).substring(openIndex, closeIndex));
                        if (htmlPages.get(i).charAt(closeIndex + 3) == 'P') {
                            if (!(htmlPages.get(i).substring(closeIndex-2,closeIndex).equals("12"))) {
                                temporary += 12;
                            }
                        }
                        course.classDays.get(j).endTime = String.valueOf(temporary);
                        openIndex = closeIndex + 1;
                        closeIndex = htmlPages.get(i).indexOf("M", openIndex) - 1;
                        course.classDays.get(j).endTime = course.classDays.get(j).endTime.concat(":" + htmlPages.get(i).substring(openIndex, closeIndex));
                        if (htmlPages.get(i).charAt(closeIndex + 8) == 'A') {
                            if (j + 1 >= course.classDays.size()) {
                                ClassDay anotherDay = new ClassDay();
                                course.classDays.add(anotherDay);
                            }
                            openIndex = htmlPages.get(i).indexOf("<br", closeIndex + 4) + 6;
                            closeIndex = htmlPages.get(i).indexOf("<", openIndex);
                            ++j;
                        } else {
                            break;
                        }
                    }


                    for (ClassDay newDay : course.classDays) {
                        if (days.contains("M")) {
                            newDay.monday = true;
                        }
                        if (days.contains("T")) {
                            newDay.tuesday = true;
                        }
                        if (days.contains("W")) {
                            newDay.wednesday = true;
                        }
                        if (days.contains("R")) {
                            newDay.thursday = true;
                        }
                        if (days.contains("F")) {
                            newDay.friday = true;
                        }
                        if (days.contains("S")) {
                            newDay.saturday = true;
                        }
                        if (days.contains("U")) {
                            newDay.sunday = true;
                        }
                    }

                    while (j + 1 < course.classDays.size()) {
                        course.classDays.get(j).location = course.classDays.get(j).location.concat(" AND " + course.classDays.get(j + 1).location);
                        course.classDays.remove(j + 1);
                    }

                } else {
//                    if (course.classDays.get(j).days == null) {
//                        course.classDays.get(j).days = "";
//                    }
                    for (j=0;j<course.classDays.size();++j) {
                        course.classDays.get(j).startTime = "00:00:00";
                        course.classDays.get(j).endTime = "00:00:00";
                    }
                }

                openIndex = htmlPages.get(i).indexOf("<td>", closeIndex) + 4;

                course.start = ("20"+(htmlPages.get(i).substring(openIndex + 6, openIndex + 8)) + ":");
                course.start = course.start.concat (htmlPages.get(i).substring(openIndex, openIndex + 2) + ":");
                course.start = course.start.concat ((htmlPages.get(i).substring(openIndex + 3, openIndex + 5)));

                openIndex = htmlPages.get(i).indexOf("-", openIndex) + 2;

                course.end = ("20"+(htmlPages.get(i).substring(openIndex + 6, openIndex + 8)) + ":");
                course.end = course.end.concat (htmlPages.get(i).substring(openIndex, openIndex + 2) + ":");
                course.end = course.end.concat ((htmlPages.get(i).substring(openIndex + 3, openIndex + 5)));

                course.courseAttributes = new ArrayList<>();

                if (isLinked || isXList) { // linked course actions

                    if (isLinked) {

                        openIndex = htmlPages.get(i).indexOf("bkstr", openIndex) - 11;
                        closeIndex = htmlPages.get(i).indexOf(">Find", openIndex) - 1;
                        course.findBooksLink = htmlPages.get(i).substring(openIndex, closeIndex).replaceAll("&amp;", "&");

                    }

                    openIndex = htmlPages.get(i).indexOf("colspan", openIndex);
                    closeIndex = htmlPages.get(i).indexOf("<", openIndex);

                    if (htmlPages.get(i).charAt(closeIndex + 1) == 'd') {

                        if (htmlPages.get(i).substring(closeIndex + 5, closeIndex + 8).equals("Com")) {
                            openIndex = closeIndex + 5;
                            closeIndex = htmlPages.get(i).indexOf("</div>", openIndex);
                            course.comment = htmlPages.get(i).substring(openIndex+10, closeIndex).replaceAll("\"","?");

                            if (htmlPages.get(i).charAt(closeIndex + 22) == 'O') {
                                openIndex = closeIndex + 41;
                                closeIndex = htmlPages.get(i).indexOf("<", openIndex);
                                course.oldCourseNumber = htmlPages.get(i).substring(openIndex, closeIndex);
                            }

                        } else if (htmlPages.get(i).charAt(closeIndex + 6) == 'O') {
                            openIndex = closeIndex + 25;
                            closeIndex = htmlPages.get(i).indexOf("<", openIndex);
                            course.oldCourseNumber = htmlPages.get(i).substring(openIndex, closeIndex);
                        }

                        openIndex = htmlPages.get(i).indexOf ('c', closeIndex);
                        if (htmlPages.get(i).substring(openIndex + 23, openIndex+26).equals("tab")) {
                            openIndex = htmlPages.get(i).indexOf("true", openIndex) + 6;
                            closeIndex = htmlPages.get(i).indexOf('<', openIndex);
                            course.fee = htmlPages.get(i).substring(openIndex,closeIndex);
                            openIndex = htmlPages.get(i).indexOf("true", openIndex) + 6;
                            closeIndex = htmlPages.get(i).indexOf('<', openIndex);
                            course.fee = course.fee.concat(" " + htmlPages.get(i).substring(openIndex,closeIndex));

                        }

                    }

                } else { // Normal course actions

                    openIndex = htmlPages.get(i).indexOf("colspan", openIndex);
                    closeIndex = htmlPages.get(i).indexOf("<", openIndex);

                    HelperLists attributeHelper = new HelperLists();

                    if (htmlPages.get(i).charAt(closeIndex + 1) == 'd') {

                        if (htmlPages.get(i).substring(closeIndex + 5, closeIndex + 8).equals("Com")) { // has comment
                            openIndex = closeIndex + 5;
                            closeIndex = htmlPages.get(i).indexOf("</div>", openIndex);
                            course.comment = htmlPages.get(i).substring(openIndex+10, closeIndex).replaceAll("\"","?");

                            if (htmlPages.get(i).substring(closeIndex + 22, closeIndex + 28).equals("a href")) { // has course attribute
                                openIndex = htmlPages.get(i).indexOf("<td>", closeIndex) + 4;
                                closeIndex = htmlPages.get(i).indexOf(":", openIndex);
                                course.courseAttributes.add(attributeHelper.getAttributeObject(htmlPages.get(i).substring(openIndex, closeIndex)));
                                closeIndex = htmlPages.get(i).indexOf("</tr>", closeIndex) + 22;

                                while (htmlPages.get(i).substring(closeIndex, closeIndex + 4).equals("<tr>")) { // get all attributes
                                    openIndex = htmlPages.get(i).indexOf("<td>", closeIndex) + 4;
                                    closeIndex = htmlPages.get(i).indexOf(":", openIndex);
                                    course.courseAttributes.add(attributeHelper.getAttributeObject(htmlPages.get(i).substring(openIndex, closeIndex)));
                                    closeIndex = htmlPages.get(i).indexOf("</tr>", closeIndex) + 22;
                                }
                            } else if (htmlPages.get(i).charAt(closeIndex + 22) == 'O') { // has old course number + attributes
                                openIndex = closeIndex + 41;
                                closeIndex = htmlPages.get(i).indexOf("<", openIndex);
                                course.oldCourseNumber = htmlPages.get(i).substring(openIndex, closeIndex);

                                if (htmlPages.get(i).substring(closeIndex + 23, closeIndex + 29).equals("a href")) {
                                    openIndex = htmlPages.get(i).indexOf("<td>", closeIndex) + 4;
                                    closeIndex = htmlPages.get(i).indexOf(":", openIndex);
                                    course.courseAttributes.add(attributeHelper.getAttributeObject(htmlPages.get(i).substring(openIndex, closeIndex)));
                                    closeIndex = htmlPages.get(i).indexOf("</tr>", closeIndex) + 22;

                                    while (htmlPages.get(i).substring(closeIndex, closeIndex + 4).equals("<tr>")) {
                                        openIndex = htmlPages.get(i).indexOf("<td>", closeIndex) + 4;
                                        closeIndex = htmlPages.get(i).indexOf(":", openIndex);
                                        course.courseAttributes.add(attributeHelper.getAttributeObject(htmlPages.get(i).substring(openIndex, closeIndex)));
                                        closeIndex = htmlPages.get(i).indexOf("</tr>", closeIndex) + 22;
                                    }
                                }
                            }

                        } else if (htmlPages.get(i).charAt(closeIndex + 6) == 'O') { // no comment
                            openIndex = closeIndex + 25;
                            closeIndex = htmlPages.get(i).indexOf("<", openIndex);
                            course.oldCourseNumber = htmlPages.get(i).substring(openIndex, closeIndex);

                            if (htmlPages.get(i).substring(closeIndex + 23, closeIndex + 29).equals("a href")) {
                                openIndex = htmlPages.get(i).indexOf("<td>", closeIndex) + 4;
                                closeIndex = htmlPages.get(i).indexOf(":", openIndex);
                                course.courseAttributes.add(attributeHelper.getAttributeObject(htmlPages.get(i).substring(openIndex, closeIndex)));
                                closeIndex = htmlPages.get(i).indexOf("</tr>", closeIndex) + 22;

                                while (htmlPages.get(i).substring(closeIndex, closeIndex + 4).equals("<tr>")) {
                                    openIndex = htmlPages.get(i).indexOf("<td>", closeIndex) + 4;
                                    closeIndex = htmlPages.get(i).indexOf(":", openIndex);
                                    course.courseAttributes.add(attributeHelper.getAttributeObject(htmlPages.get(i).substring(openIndex, closeIndex)));
                                    closeIndex = htmlPages.get(i).indexOf("</tr>", closeIndex) + 22;
                                }
                            }

                        } else if (htmlPages.get(i).substring(closeIndex + 6, closeIndex + 12).equals("a href")) {
                            openIndex = htmlPages.get(i).indexOf("<td>", closeIndex) + 4;
                            closeIndex = htmlPages.get(i).indexOf(":", openIndex);
                            course.courseAttributes.add(attributeHelper.getAttributeObject(htmlPages.get(i).substring(openIndex, closeIndex)));
                            closeIndex = htmlPages.get(i).indexOf("</tr>", closeIndex) + 22;

                            while (htmlPages.get(i).substring(closeIndex, closeIndex + 4).equals("<tr>")) {
                                openIndex = htmlPages.get(i).indexOf("<td>", closeIndex) + 4;
                                closeIndex = htmlPages.get(i).indexOf(":", openIndex);
                                course.courseAttributes.add(attributeHelper.getAttributeObject(htmlPages.get(i).substring(openIndex, closeIndex)));
                                closeIndex = htmlPages.get(i).indexOf("</tr>", closeIndex) + 22;
                            }
                        }
                    }

                    for (int z=0; z<course.courseAttributes.size();++z){
                        if(course.courseAttributes.get(z).equals("SHUM") || course.courseAttributes.get(z).equals("SSSE")) {
                            course.courseAttributes.remove(z);
                            --z;
                        }
                    }

                    openIndex = htmlPages.get(i).indexOf("bkstr", openIndex) - 11;
                    closeIndex = htmlPages.get(i).indexOf(">Find", openIndex) - 1;
                    course.findBooksLink = htmlPages.get(i).substring(openIndex, closeIndex).replaceAll("&amp;", "&");

                    closeIndex = openIndex;

                    openIndex = htmlPages.get(i).indexOf("</tr>", openIndex) + 1;
                    openIndex = htmlPages.get(i).indexOf("<", openIndex) + 1;
                    openIndex = htmlPages.get(i).indexOf("<", openIndex) + 1;
                    openIndex = htmlPages.get(i).indexOf("<", openIndex) + 1;
                    openIndex = htmlPages.get(i).indexOf("<", openIndex) + 1;
                    openIndex = htmlPages.get(i).indexOf("<", openIndex) + 1;
                    openIndex = htmlPages.get(i).indexOf("<", openIndex) + 1;

                    if (htmlPages.get(i).substring(openIndex, openIndex + 4).equals("td n")) {
                        openIndex = htmlPages.get(i).indexOf(">", openIndex) + 1;
                        closeIndex = htmlPages.get(i).indexOf("<", openIndex);
                        course.fee = htmlPages.get(i).substring(openIndex, closeIndex);
                        openIndex = htmlPages.get(i).indexOf("$", openIndex);
                        closeIndex = htmlPages.get(i).indexOf("<", openIndex);
                        course.fee = course.fee.concat(" " + htmlPages.get(i).substring(openIndex, closeIndex));
                    } else {
                        openIndex = closeIndex;
                    }
                }

            }
        }

        try {
            FileOutputStream fos = new FileOutputStream("courses.ser");
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            for (Course course : totalCourseData) {
                oos.writeObject(course);
            }
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void loadDataFromFile() {

        try {
            FileInputStream fis = new FileInputStream("courses.ser");
            ObjectInputStream ois = new ObjectInputStream(fis);

            try {
                while (true) {
                    Course course = (Course)ois.readObject();
                    totalCourseData.add(course);
                }
            } catch (IOException x) {
                ois.close();
            }
        } catch (IOException |ClassNotFoundException e) {
            // won't reach here
            e.printStackTrace();
        }
    }

    private static void uploadData() {

        for (int i=0;i<totalCourseData.size();++i) {
            Map<String, Object> courseData = new HashMap<>();
            courseData.put("subjectAcronym",totalCourseData.get(i).subjectAcronym);
            courseData.put("subjectName", totalCourseData.get(i).subjectName);
            courseData.put("subjectNumberString",  totalCourseData.get(i).subjectNumber);
            int subjNumberInteger = Integer.parseInt(totalCourseData.get(i).subjectNumber.replaceAll("[^\\d]", "" ));
            courseData.put("subjectNumberInteger",  subjNumberInteger);
            courseData.put("courseName", totalCourseData.get(i).courseName);
            courseData.put("credit", totalCourseData.get(i).credit);
            courseData.put("bulletinLink", totalCourseData.get(i).bulletinLink);
            courseData.put("bulletinDescription", totalCourseData.get(i).bulletinDescription);
            ArrayList<String> offeringsArray = new ArrayList<>();
            for (int j = 0; j < totalCourseData.size(); ++j) {
                boolean isEqual = totalCourseData.get(i).subjectAcronym.equals(totalCourseData.get(j).subjectAcronym)
                        && totalCourseData.get(i).subjectName.equals(totalCourseData.get(j).subjectName)
                        && totalCourseData.get(i).courseName.equals(totalCourseData.get(j).courseName);
                if (isEqual) {
                    Map<String, Object> offeringData = new HashMap<>();
                    offeringData.put("subjectAcronym",totalCourseData.get(j).subjectAcronym);
                    offeringData.put("subjectName",totalCourseData.get(j).subjectName);
                    offeringData.put("subjectNumberString",totalCourseData.get(j).subjectNumber);
                    offeringData.put("subjectNumberInteger",subjNumberInteger);
                    offeringData.put("courseName",totalCourseData.get(j).courseName);
                    offeringData.put("credit",totalCourseData.get(j).credit);
                    offeringData.put("status",totalCourseData.get(j).status);

                    offeringData.put("bulletinLink", totalCourseData.get(j).bulletinLink);
                    offeringData.put("bulletinDescription", totalCourseData.get(j).bulletinDescription);
                    offeringData.put("sectionNumber", totalCourseData.get(j).sectionNumber);
                    if (totalCourseData.get(j).instructor != null) {
                        offeringData.put("instructors", totalCourseData.get(j).instructor);
                    }
                    if (totalCourseData.get(j).classDays != null) {
                        ArrayList<Object> classDays = new ArrayList<>();
                        for (ClassDay day : totalCourseData.get(j).classDays) {
                            Map<String, Object> classDay = new HashMap<>();
                            classDay.put("monday", day.monday);
                            classDay.put("tuesday", day.tuesday);
                            classDay.put("wednesday", day.wednesday);
                            classDay.put("thursday", day.thursday);
                            classDay.put("friday", day.friday);
                            classDay.put("saturday", day.saturday);
                            classDay.put("sunday", day.sunday);
                            classDay.put("location", day.location);

                            SimpleDateFormat ft = new SimpleDateFormat ("H:mm");
                            try {
                                Date start = ft.parse(day.startTime);
                                Date end = ft.parse(day.endTime);
                                classDay.put("startTime", start);
                                classDay.put("endTime", end);
                            } catch (ParseException e) {
                                System.out.println("Unparseable using " + ft);
                            }
                            classDays.add(classDay);
                        }
                        offeringData.put("classDays", classDays);
                    }
                    offeringData.put("startDate", totalCourseData.get(j).start);
                    offeringData.put("endDate", totalCourseData.get(j).end);
                    offeringData.put("comment", totalCourseData.get(j).comment);
                    offeringData.put("findBooksLink", totalCourseData.get(j).findBooksLink);

//                    if (totalCourseData.get(j).Linked != null) {
//                        newOffering.Linked = new ArrayList<>(totalCourseData.get(j).Linked);
//                    }
                    if (totalCourseData.get(j).courseAttributes != null) {
                        ArrayList<Object> courseAttributes = new ArrayList<>();
                        for (CourseAttribute attribute : totalCourseData.get(j).courseAttributes) {
                            Map<String, Object> newAttribute = new HashMap<>();
                            newAttribute.put("acronym", attribute.acronym);
                            newAttribute.put("name", attribute.name);
                            courseAttributes.add(newAttribute);
                        }
                        offeringData.put("courseAttributes", courseAttributes);
                    }
                    offeringData.put("fee", totalCourseData.get(j).fee);
                    // add overring
                    db.collection("offeringsSpring2018").document(totalCourseData.get(j).CRN).set(offeringData);
                    System.out.println("Added offering: " + totalCourseData.get(j).CRN);
                    offeringsArray.add(totalCourseData.get(j).CRN);
                    totalCourseData.remove(j);
                }
            }
            courseData.put("offerings", offeringsArray);
            ApiFuture<DocumentReference> addedDocRef =
                    db.collection("coursesSpring2018").add(courseData);

            try {
                addedDocRef.get().getId();
            } catch(InterruptedException | ExecutionException e) {
                System.out.println(e);
            }

        }
    }
}
