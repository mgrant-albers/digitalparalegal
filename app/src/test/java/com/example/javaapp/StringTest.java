package com.example.javaapp;

import org.junit.Test;

import static org.junit.Assert.*;


public class StringTest {

    @Test
    public void testFormatNumber(){
        String actual = formatNumber("800//555-3455");
        String expected = "800-555-3455";
        assertEquals(expected, actual);
        }
    private String formatNumber(String s){
        String output;
        s = s.replaceAll("\\p{Punct}", "");
        if(s.length() > 8 && !s.contains("@")){
            if(s.charAt(0) == '+'){
                s = s.substring(2);
            }
            output = s.substring(0, 3) + "-" + s.substring(3, 6) + "-" + s.substring(6,10);
            return output;
        }
        else
            return s;
    }
}