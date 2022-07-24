import org.apache.ibatis.parsing.XNode;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
public class Readme {


    /**
     * Mybatis 单独使用  {@link com.ling.test01.Test01}
     */
    void read01() {}

    /**
     * mybatis dao 模式 {@link com.ling.test02.Test02}
     *
     * <ol>
     *     <li> 解析配置文件：{@link XMLConfigBuilder#parseConfiguration(XNode) } </li>
     *     <li> 解析映射器 {@link XMLConfigBuilder#mapperElement(XNode)} </li>
     * </ol>
     */
    void read02(){}

    /**
     * sql 命令解析
     *
     */
    void read03(){}
}
