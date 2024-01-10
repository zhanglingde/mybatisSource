package com.ling.test02.entity;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class User {

    private Integer userId;
    private String userName;
    private Integer age;
    private String password;

}
