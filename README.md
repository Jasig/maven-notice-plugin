# notice-maven-plugin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.jasig.maven/notice-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.jasig.maven/notice-maven-plugin)
[![Build Status](https://travis-ci.org/Jasig/maven-notice-plugin.svg?branch=master)](https://travis-ci.org/Jasig/maven-notice-plugin)

Generates NOTICE files based on a given template. Defaults to Apereo copyright.

```xml
<plugin>
  <groupId>org.jasig.maven</groupId>
  <artifactId>notice-maven-plugin</artifactId>
    <version>2.0.0</version>
    <configuration>
        <noticeTemplate>${jasig-notice-template-url}</noticeTemplate>
        <licenseMapping>
            <param>${jasig-license-lookup-url}</param>
        </licenseMapping>
    </configuration>
</plugin>
```