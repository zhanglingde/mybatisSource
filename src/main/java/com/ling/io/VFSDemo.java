package com.ling.io;

import org.apache.ibatis.io.DefaultVFS;
import org.apache.ibatis.io.VFS;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public class VFSDemo {
    public static void main(String[] args) {
        try {
            DefaultVFS vfs = (DefaultVFS) VFS.getInstance();

            // 示例1：列出classpath下的资源(列出org/apache/ibatis/io包下的所有资源)
            URL url = Thread.currentThread().getContextClassLoader().getResource("com/ling/io");
            if (url != null) {
                System.out.println("URL: " + url.toString());

                // 使用VFS列出该路径下的所有资源
                List<String> resources = vfs.list(url, "com/ling/io");

                System.out.println("Resources in com/ling/io:");
                for (String resource : resources) {
                    System.out.println("  " + resource);
                }
            }

            // 示例2：列出jar包中的资源(如果MyBatis的jar包在classpath中，我们可以列出其中的资源)
            URL mybatisUrl = Thread.currentThread().getContextClassLoader().getResource("ognl/internal");
            if (mybatisUrl != null) {
                System.out.println("\nURL: " + mybatisUrl.toString());
                List<String> mybatisResources = vfs.list(mybatisUrl, "ognl/internal");

                System.out.println("Resources in ognl/internal:");
                // 只显示前10个资源
                int count = 0;
                for (String resource : mybatisResources) {
                    if (count++ < 10) {
                        System.out.println("  " + resource);
                    } else {
                        System.out.println("  ... and " + (mybatisResources.size() - 10) + " more");
                        break;
                    }
                }
            }

            // 示例3：直接使用DefaultVFS
            DefaultVFS defaultVFS = new DefaultVFS();
            if (defaultVFS.isValid()) {
                System.out.println("\nDefaultVFS is valid for use");
            }
            // 查找特定资源的JAR文件
            URL someResource = Thread.currentThread().getContextClassLoader().getResource("com/ling/io/VFSDemo.class");
            if (someResource != null) {
                System.out.println("\nResource URL: " + someResource.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
