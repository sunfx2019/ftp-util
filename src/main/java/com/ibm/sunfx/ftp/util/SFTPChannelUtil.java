package com.ibm.sunfx.ftp.util;

import com.alibaba.fastjson.JSON;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * SFTUtil文件上传下载工具
 * 
 * @author sunfeixiang
 * @since 2018年1月17日
 */
public class SFTPChannelUtil {

  private Logger logger = LoggerFactory.getLogger(getClass());
  
  public static final String NO_FILE = "No such file";

  private ChannelSftp sftp = null;

  private Session sshSession = null;

  private String username;

  private String password;

  private String host;

  private int port;
  
  /**
   * 构造方法
   * 
   * @param username 用户名
   * @param password 密码
   * @param host 机器IP地址
   * @param port 访问密码
   */
  public SFTPChannelUtil(String host, int port, String username, String password) {
    this.username = username;
    this.password = password;
    this.host = host;
    this.port = port;
  }

  /**
   * 连接sftp服务器
   * 
   * @return ChannelSftp sftp类型
   * @throws GoPayException
   */
  public ChannelSftp connect() {
    JSch jsch = new JSch();
    try {
      jsch.getSession(username, host, port);
      sshSession = jsch.getSession(username, host, port);
      if (logger.isDebugEnabled()) {
        logger.debug(String.format("sftp---Session ip[%s]port[%s]created", host, port));
      }
      sshSession.setPassword(password);
      Properties properties = new Properties();
      properties.put("StrictHostKeyChecking", "no");
      properties.put("userauth.gssapi-with-mic", "no");
      sshSession.setTimeout(20 * 1000); //设置超时时间,20s
      sshSession.setConfig(properties);
      sshSession.connect();
      if (logger.isDebugEnabled()) {
        logger.debug(String.format("sftp---Session ip[%s]port[%s]connected", host, port));
      }
      Channel channel = sshSession.openChannel("sftp");
      channel.connect();
      sftp = (ChannelSftp) channel;
      sftp.setFilenameEncoding("UTF-8");
      if (logger.isDebugEnabled()) {
        logger.debug(String.format("sftp---Session ip[%s]port[%s]success", host, port));
      }
    } catch (JSchException | SftpException e) {
      logger.error("SFTPChannelUtil connect error", e);
    }
    return sftp;
  }

  /**
   * 
   * 下载单个文件
   * 
   * @param directory 远程下载目录(以路径符号结束)
   * @param remoteFileName FTP服务器文件名称 如：xxx.txt ||xxx.txt.zip
   * @param localFile 本地文件路径 如 D:\\xxx.txt
   * @return File
   */
  public File downloadFile(String directory, String remoteFileName, String localFile) {

    connect();

    File file = null;
    OutputStream output = null;

    try {
      file = new File(localFile);
      if (file.exists()) {
        FileUtils.deleteQuietly(file);
      }
      if (!file.createNewFile()) {
        return file;
      }
      sftp.cd(directory);
      output = new FileOutputStream(file);
      sftp.get(remoteFileName, output);
      if (logger.isDebugEnabled()) {
        logger.debug(String.format("SFTPChannelUtil---dowload file[%s] save to[%s] success...", remoteFileName, localFile));
      }
    } catch (SftpException e) {
      if (e.toString().equals(NO_FILE)) {
        logger.error("SftpException:", e);
      }
    } catch (FileNotFoundException e) {
      logger.error("FileNotFoundException:", e);
    } catch (IOException e) {
      logger.error("IOException:", e);
    } finally {
      if (output != null) {
        try {
          output.close();
        } catch (IOException e) {
          logger.error("Close stream error:", e);
        }
      }
      disconnect();
    }

    return file;

  }

  /**
   * 查看目录下的所有文件
   * 
   * @param directory
   * @param uploadFile
   * @return boolean
   */
  public List<Object> listFiles(String directory) {
    try {
      connect();
      return Arrays.asList(sftp.ls(directory).toArray());
    } catch (SftpException e) {
      return new ArrayList<>();
    } finally {
      disconnect();
    }
  }

  /**
   * 上传单个文件
   * 
   * @param directory 远程服务器的目录
   * @param uploadFile 要上传的文件
   * @return boolean 成功或失败
   */
  public boolean uploadFile(String directory, String uploadFile) {

    if (!new File(uploadFile).exists()) {
      if (logger.isDebugEnabled()) {
        logger.debug(String.format("file [%s] not found:", uploadFile));
      }
      return false;
    }

    connect();
    FileInputStream in = null;
    File file = new File(uploadFile);

    // 检查并创建服务器目录
    if (!this.sftpCreateDirectory(directory)) {
      return false;
    }

    try {
      in = new FileInputStream(file);
      sftp.put(in, file.getName());
      if (logger.isDebugEnabled()) {
        logger.info(String.format("upload file:[%s] ftp connect:[%s] save to:[%s]", uploadFile, host, directory));
      }
    } catch (FileNotFoundException e) {
      logger.error("FileNotFoundException:", e);
      return false;
    } catch (SftpException e) {
      logger.error("SftpException:", e);
      return false;
    } finally {
      disconnect();
      if (in != null) {
        try {
          in.close();
        } catch (IOException e) {
          logger.error("Close stream error.", e);
        }
      }
    }

    return true;

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

    connect();

    // 检查并创建服务器目录
    if (!this.sftpCreateDirectory(directory)) {
      return false;
    }

    try {
      for (String uploadFile : fileList) {
        File file = new File(uploadFile);
        FileInputStream in = new FileInputStream(file);
        sftp.put(in, file.getName());
        in.close();
        if (logger.isDebugEnabled()) {
          logger.info(String.format("ftp connect{%s} upload file{%s} save to{%s}", host, uploadFile, directory));
        }
      }
    } catch (FileNotFoundException e) {
      logger.error("FileNotFoundException", e);
      return false;
    } catch (SftpException e) {
      logger.error("SftpException", e);
      return false;
    } catch (IOException e) {
      logger.error("IOException", e);
    } finally {
      disconnect();
    }

    return true;

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
  public boolean uploadFileList(String baseDirectory, List<String> directoryList, List<String> fileList) {
  
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
  
    // 开始连接
    connect();
    FileInputStream in = null;
  
    for (String directoryString : directoryList) {
      // 检查并创建服务器目录<创建多层目录>
      if (!this.sftpCreateDirectorys(baseDirectory, directoryString)) {
        return false;
      }
    }
  
    try {
      for (int i = 0; i < fileList.size(); i++) {
        String directoryStr = directoryList.get(i); //保存在服务器的目录
        String fileStr = fileList.get(i); //保存的文件
        File file = new File(fileStr);
        in = new FileInputStream(file);
        sftp.cd(directoryStr);  //进入目录
        sftp.put(in, file.getName()); //上传文件
        in.close();
        if (logger.isDebugEnabled()) {
          logger.info(String.format("ftp connect{%s} upload file{%s} save to{%s}", host, file, directoryStr));
        }
      }
    } catch (FileNotFoundException e) {
      logger.error("FileNotFoundException", e);
      return false;
    } catch (SftpException e) {
      logger.error("SftpException", e);
      return false;
    } catch (IOException e) {
      logger.error("IOException", e);
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException e) {
          logger.error("Close stream error.", e);
        }
      }
      disconnect();
    }
  
    return true;
  
  }

  /**
   * sftp 检查目录是否存在不存在则新建一个该目录
   * 
   * @param directory
   * @return boolean
   */
  public boolean sftpCreateDirectory(String directory) {
    try {
      sftp.cd(directory);
      return true;
    } catch (SftpException e) {
      try {
        sftp.mkdir(directory);
        sftp.cd(directory);
        return true;
      } catch (SftpException e1) {
        logger.debug(String.format("ftp create directory [%s] is error", directory));
        logger.error(e.getMessage(), e1);
        return false;
      }
    }
  }
  
  /**
   * 创建多层目录
   * <p>
   *  Example: baseDirectory=/srv/salt/salt-ftp-home/data/software
   *           directory=/srv/salt/salt-ftp-home/data/software/20180302200433215/ApplicationUpgrade/1.0
   *  则在baseDirectory目录下新建 /20180302200433215/ApplicationUpgrade/1.0 多层目录
   * </p>
   * 
   * @param baseDirectory 根目录
   * @param directory 全目录
   * @return boolean
   */
  public boolean sftpCreateDirectorys(String baseDirectory, String directory) {
    String newDirectory = StringUtils.replace(directory, baseDirectory, "");  //directory去掉baseDirectory目录
    String [] folderNames = newDirectory.split("/");
    if(logger.isDebugEnabled()) {
      logger.debug(JSON.toJSONString(folderNames));
    }
    try {
      sftp.cd(baseDirectory);
    } catch (SftpException e2) {
      logger.error("Root directory not found:", e2);
    } 
    //循环创建文件夹
    for ( String folderName : folderNames ) {
        if (StringUtils.isNotBlank(folderName) ) { 
            try {  
                sftp.cd(folderName);  
            } catch ( SftpException e ) {  
                try {
                  sftp.mkdir(folderName);
                  sftp.cd(folderName);
                  logger.debug("pwd=" + sftp.pwd());
                } catch (SftpException e1) {
                  logger.error("FolderName create error:", e1);
                  return false;
                }  
            }  
        }  
    } 
    return true;
  }

  /**
   * 关闭连接
   */
  public void disconnect() {
    if (this.sftp != null && this.sftp.isConnected()) {
      this.sftp.disconnect();
      this.sftp = null;
    }
    if (this.sshSession != null && this.sshSession.isConnected()) {
      this.sshSession.disconnect();
      this.sshSession = null;
    }
  }

}
