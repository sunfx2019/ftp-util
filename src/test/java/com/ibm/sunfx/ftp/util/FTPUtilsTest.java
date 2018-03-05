package com.ibm.sunfx.ftp.util;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alibaba.fastjson.JSON;
import junit.framework.TestCase;

public class FTPUtilsTest extends TestCase {
    
    private Logger logger = LoggerFactory.getLogger(getClass());
    
    @Test
    public void test() {
        String[] dirs = "/dong/zzz/ddd/ewv".split("/");
        logger.debug(JSON.toJSONString(dirs));
    }
    
    @Test
    public void open() {
        
    }

}
