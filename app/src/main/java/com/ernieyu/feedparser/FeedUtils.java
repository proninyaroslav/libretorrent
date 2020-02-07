package com.ernieyu.feedparser;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Static utility methods for processing feed data.
 */
public class FeedUtils {
    /** Date format for Atom dates. */
    private static final DateFormat ATOM_DATE = 
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH);
    
    /** Date format for RSS 2.0 dates. */
    private static final DateFormat RSS2_DATE = 
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);

    /**
     * Converts the specified Atom date string to a Date.  Atom uses a date
     * format specified by RFC 3339.
     * 
     * @param dateStr Atom date/time string
     * @return Date object, or null if string cannot be parsed
     */
    public static Date convertAtomDate(String dateStr) {
        dateStr = dateStr.trim();
        
        // Process time zone offset.
        int tzpos;
        if (dateStr.endsWith("Z")) {
            // Replace trailing Z with GMT time zone.
            tzpos = dateStr.lastIndexOf('Z');
            dateStr = dateStr.substring(0, dateStr.length() - 1) + "GMT";
            
        } else {
            // Find time zone.
            tzpos = dateStr.lastIndexOf('+');
            if (tzpos == -1) tzpos = dateStr.lastIndexOf('-');
            String datetime = dateStr.substring(0, tzpos);
            String timezone = dateStr.substring(tzpos);

            // Remove colon from time zone.
            int colon = timezone.indexOf(':');
            dateStr = datetime + timezone.substring(0, colon) + timezone.substring(colon + 1);
        }
        
        // Compute milliseconds if fractional seconds used.
        int millisec = 0;
        int fracpos = dateStr.indexOf('.');
        if (fracpos > -1) {
            // Extract fractional seconds.
            int digits = tzpos - fracpos - 1;
            int fraction = Integer.parseInt(dateStr.substring(fracpos + 1, tzpos));
            // Compute milliseconds.
            millisec = (int) (1000.0 * (double) fraction / Math.pow(10, digits));
            // Remove fraction from time.
            dateStr = dateStr.substring(0, fracpos) + dateStr.substring(tzpos);
        }
        
        try {
            // Parse date using Atom format.
            Date date = ATOM_DATE.parse(dateStr);
            // Return date with milliseconds.
            return (millisec > 0) ? new Date(date.getTime() + millisec) : date;
        } catch (ParseException ex) {
            // Return null if date cannot be parsed.
            return null;
        }
    }

    /**
     * Converts the specified RSS 1.0 date string to a Date.  RSS 1.0 uses a
     * date format specified by ISO 8601.
     * 
     * @param dateStr RSS 1.0 date/time string
     * @return Date object, or null if string cannot be parsed
     */
    public static Date convertRss1Date(String dateStr) {
        // Use Atom parser for now.  Note that Atom actually follows a 
        // stricter version of the RSS 1.0 date/time format.
        return convertAtomDate(dateStr);
    }

    /**
     * Converts the specified RSS 2.0 date string to a Date.  RSS 2.0 uses a
     * date format specified by RFC 822.
     * 
     * @param dateStr RSS 2.0 date/time string
     * @return Date object, or null if string cannot be parsed
     */
    public static Date convertRss2Date(String dateStr) {
        try {
            // Parse date using RSS format.
            return RSS2_DATE.parse(dateStr.trim());
            
        } catch (ParseException ex) {
            // Return null if date cannot be parsed.
            return null;
        }
    }
    
    /**
     * Compares the two specified objects for equality.
     * 
     * @param obj1 first object to compare
     * @param obj2 second object to compare
     * @return true if both objects are equal or both objects are null
     */
    public static boolean equalsOrNull(Object obj1, Object obj2) {
        if (obj1 != null) {
            return obj1.equals(obj2);
        } else {
            return (obj2 == null);
        }
    }
}
