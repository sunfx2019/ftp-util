package com.ibm.sunfx.ftp.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.log4j.Logger;

public class FTPUtils {

    private String host = "192.168.1.100";

    private String username = "root";

    private String password = "123456";

    private int port = 21;

    private FTPClient ftp = new FTPClient();

    // 本地编码
    private String localCharset = "GBK";

    // 服务器编码
    private String serverCharset = "ISO-8859-1";

    private static FTPUtils util = new FTPUtils();

    private Logger logger = Logger.getLogger(getClass());

    private FTPUtils() {

    }

    public static FTPUtils getInstance() {
        return util;
    }

    /**
     * 获取ftp连接
     * 
     * @param f
     * @return
     * @throws Exception
     */
    public boolean connectFtp() {
        try {
            if (ftp != null && ftp.isConnected()) {
                return true;
            }
            boolean flag = false;
            int reply;
            ftp.setConnectTimeout(5 * 1000);
            ftp.connect(host, port);
            ftp.login(username, password);
            ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
            reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                return flag;
            }
            flag = true;
            logger.debug(String.format("ftp---Session ip[%s] port[%s] connected success", host, port));
            return flag;
            
        } catch (Exception e) {
            logger.debug(String.format("ftp---Session ip[%s] port[%s] connected failure", host, port));
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    /**
     * 关闭ftp连接
     */
    public void closeFtp() {
        if (ftp != null && ftp.isConnected()) {
            try {
                ftp.logout();
                ftp.disconnect();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    /**
     * ftp上传文件或一个目录
     * 
     * <p>
     * directory服务器目录必须存在
     * </p>
     * 
     * @param f
     * @return boolean
     */
    public boolean uploadFile(String directory, String uploadFile) {

        FileInputStream input = null;
        File f = new File(uploadFile);

        if (!new File(uploadFile).exists()) {
            logger.debug(String.format("uploadFile {%s} not found...", uploadFile));
            return false;
        }

        if (!this.connectFtp()) {
            return false;
        }

        try {
            // 判断目录是否存在
            if (!ftp.changeWorkingDirectory(directory)) {
                this.mkdirs(directory);
            }
        } catch (IOException e1) {
            logger.debug(String.format("ftp---Session ip[%s] port[%s] create directory {%s} failure", host, port, directory));
            return false;
        }

        try {
            input = new FileInputStream(f);
            ftp.storeFile(f.getName(), input);
            logger.info(String.format("ftp upload file [%s] ftp [%s] save to [%s]", uploadFile, host, directory));
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        } finally {
            IOUtils.closeQuietly(input);
            closeFtp();
        }

    }

    /**
     * 上传多个文件
     * 
     * @param directory
     * @param fileList
     * @return boolean
     */
    public boolean uploadFileList(String directory, List<String> fileList) {

        if (fileList == null || fileList.isEmpty()) {
            return false;
        }

        for (String fileString : fileList) {
            if (!new File(fileString).exists()) {
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("file [%s] not found.", fileString));
                }
                return false;
            }
        }

        if (!this.connectFtp()) {
            return false;
        }

        try {
            // 判断目录是否存在
            if (!this.mkdirs(directory)) {
                // 创建目录
                this.mkdirs(directory);
                // 再判断目录是否存在
                if (!ftp.changeWorkingDirectory(directory)) {
                    return false;
                }
            }
        } catch (IOException e1) {
            logger.debug(String.format("ftp---Session ip[%s] port[%s] create directory {%s} failure", host, port, directory));
            return false;
        }

        FileInputStream input = null;

        try {
            ftp.changeWorkingDirectory(directory); // 进入目录
            for (String file : fileList) {
                File f = new File(file);
                input = new FileInputStream(f);
                ftp.storeFile(f.getName(), input);
                input.close();
                logger.info(String.format("ftp upload file [%s] ftp [%s] save to [%s]", file, host, directory));
            }
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        } finally {
            IOUtils.closeQuietly(input);
            closeFtp();
        }

    }

    /**
     * 上传多个文件
     * <p>
     * directoryList目录顺序和fileList文件一一对应
     * </p>
     * 
     * @param directoryList
     * @param fileList
     * @return boolean
     */
    public boolean uploadFileList(List<String> directoryList, List<String> fileList) {

        if (CollectionUtils.isEmpty(directoryList) || CollectionUtils.isEmpty(fileList) || directoryList.size() != fileList.size()) {
            return false;
        }

        // 检查文件是否存在
        for (String fileString : fileList) {
            if (!new File(fileString).exists()) {
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("file [%s] not found.", fileString));
                }
                return false;
            }
        }

        if (!this.connectFtp()) {
            return false;
        }

        for (String directoryString : directoryList) {
            // 检查并创建服务器目录<创建多层目录>
            if (!this.mkdirs(directoryString)) {
                return false;
            }
        }

        FileInputStream in = null;

        try {
            for (int i = 0; i < fileList.size(); i++) {
                String directoryStr = directoryList.get(i); // 保存在服务器的目录
                String fileStr = fileList.get(i); // 保存的文件
                File file = new File(fileStr);
                in = new FileInputStream(file);
                ftp.changeWorkingDirectory(directoryStr); // 进入目录
                ftp.storeFile(file.getName(), in);// 上传文件
                in.close();
                logger.info(String.format("ftp connect{%s} upload file{%s} save to{%s}", host, file, directoryStr));
            }
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        } finally {
            IOUtils.closeQuietly(in);
            closeFtp();
        }

    }

    /**
     * 创建多级目录
     * 
     * @param directory
     * @return boolean
     */
    public boolean mkdirs(String directory) {

        if (StringUtils.isBlank(directory)) {
            return false;
        }

        // 判断目录是否存在
        if (this.cheackDirectoryIsExist(directory)) {
            return true;
        }

        Boolean success = false;
        boolean tmpMkdirs = false;
        String[] subDirs = directory.split("/");

        // check if is absolute path
        if (directory.substring(0, 1).equalsIgnoreCase("/")) {
            subDirs[0] = "/" + subDirs[0];
        }

        try {
            // 开启服务器对UTF-8的支持，如果服务器支持就用UTF-8编码，否则就使用本地编码（GBK）.
            if (FTPReply.isPositiveCompletion(ftp.sendCommand("OPTS UTF8", "ON"))) {
                localCharset = "UTF-8";
            }
            // 进入根目录
            ftp.changeWorkingDirectory(subDirs[0]);
            ftp.setControlEncoding(localCharset);
            String workingDirectory = ftp.printWorkingDirectory();
            logger.debug("current WorkingDirectory:" + workingDirectory);
            for (String subDir : subDirs) {
                // encoding
                String strSubDir = new String(subDir.getBytes(localCharset), serverCharset);
                tmpMkdirs = ftp.makeDirectory(strSubDir);
                logger.debug(String.format("tmpMkdirs {%s} is {%s}", strSubDir, String.valueOf(tmpMkdirs)));
                boolean tmpDoCommand = ftp.sendSiteCommand("chmod 755 " + strSubDir);
                logger.debug("tmpDoCommand:" + tmpDoCommand);
                ftp.changeWorkingDirectory(strSubDir);
                success = success || tmpMkdirs;
            }
        } catch (IOException e) {
            logger.debug(String.format("mkdirs directory {%s} failure.", directory));
            logger.error(e.getMessage(), e);
        }

        return success;

    }

    /**
     * 判断目录是否存在
     * 
     * @param directory
     * @return boolean
     */
    public boolean cheackDirectoryIsExist(String directory) {
        try {
            return ftp.changeWorkingDirectory(directory);
        } catch (IOException e1) {
            return false;
        }
    }


    /**
     * 下载链接配置
     * 
     * @param f
     * @param localBaseDir 本地目录
     * @param remoteBaseDir 远程目录
     * @throws Exception
     */
    public boolean down(String localBaseDir, String remoteBaseDir) {
        if (this.connectFtp()) {
            try {
                FTPFile[] files = null;
                boolean changedir = ftp.changeWorkingDirectory(remoteBaseDir);
                if (changedir) {
                    ftp.setControlEncoding("GBK");
                    files = ftp.listFiles();
                    for (int i = 0; i < files.length; i++) {
                        downloadFile(files[i], localBaseDir, remoteBaseDir);
                    }
                }
                return true;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return false;
            }
        } else {
            return false;
        }

    }


    /**
     * 
     * 下载FTP文件 当你需要下载FTP文件的时候，调用此方法 根据<b>获取的文件名，本地地址，远程地址</b>进行下载
     * 
     * @param ftpFile
     * @param relativeLocalPath
     * @param relativeRemotePath
     */
    public void downloadFile(FTPFile ftpFile, String relativeLocalPath, String relativeRemotePath) {
        if (ftpFile.isFile()) {
            OutputStream outputStream = null;
            try {
                File locaFile = new File(relativeLocalPath + ftpFile.getName());
                // 判断文件是否存在，存在则返回
                if (locaFile.exists()) {
                    return;
                } else {
                    outputStream = new FileOutputStream(relativeLocalPath + ftpFile.getName());
                    ftp.retrieveFile(ftpFile.getName(), outputStream);
                    outputStream.flush();
                    outputStream.close();
                }
            } catch (Exception e) {
                logger.error(e);
            } finally {
                IOUtils.closeQuietly(outputStream);
            }
        } else {
            String newlocalRelatePath = relativeLocalPath + ftpFile.getName();
            String newRemote = relativeRemotePath + ftpFile.getName();
            File fl = new File(newlocalRelatePath);
            if (!fl.exists()) {
                fl.mkdirs();
            }
            try {
                newlocalRelatePath = newlocalRelatePath + '/';
                newRemote = newRemote + "/";
                String currentWorkDir = ftpFile.getName();
                boolean changedir = ftp.changeWorkingDirectory(currentWorkDir);
                if (changedir) {
                    FTPFile[] files = null;
                    files = ftp.listFiles();
                    for (int i = 0; i < files.length; i++) {
                        downloadFile(files[i], newlocalRelatePath, newRemote);
                    }
                }
                if (changedir) {
                    ftp.changeToParentDirectory();
                }
            } catch (Exception e) {
                logger.error(e);
            }
        }
    }


    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

}
