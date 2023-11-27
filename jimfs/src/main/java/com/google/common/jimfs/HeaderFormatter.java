package com.google.common.jimfs;

import java.nio.file.attribute.FileTime;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Class responsible for formatting the "last-modified" header according to the specified date format.
 */
public class HeaderFormatter {

    private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss 'GMT'";

    static String formatLastModified(FileTime lastModified) {
        DateFormat format = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date(lastModified.toMillis()));
    }
}