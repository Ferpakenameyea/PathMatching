package com.daxia;

import java.io.File;

import org.junit.Test;

import com.daxia.mapmaching.CsvConverter;

public class CSVConvertTest {
    @Test
    public void convertTest() throws Exception {
        CsvConverter converter = new CsvConverter("./rtic_xy.csv");
        converter.trans(new File("./out.osm"));
    }
}
