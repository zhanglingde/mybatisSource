package com.ling.io;

import org.apache.ibatis.io.ResolverUtil;

import java.util.Set;

public class ResolverUtilDemo {
    public static void main(String[] args) {

        ResolverUtil<Object> resolverUtil = new ResolverUtil<>();
        resolverUtil.setClassLoader(Thread.currentThread().getContextClassLoader());  // 设置类加载器

        // 1. 扫描指定包下的 Object 子类(这个过程会使用VFS来查找类路径资源)
        resolverUtil.findImplementations(Object.class, "com.ling.io");
        // 获取扫描到的结果
        Set<Class<? extends Object>> mappers = resolverUtil.getClasses();
        System.out.println("Found " + mappers.size() + " classes:");
        for (Class<?> mapper : mappers) {
            System.out.println("  " + mapper.getName());
        }
    }
}
