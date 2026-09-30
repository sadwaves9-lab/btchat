@echo off
set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
if defined JAVA_HOME goto findJava
set JAVA_EXE=java.exe
goto exec
:findJava
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
:exec
"%JAVA_EXE%" "-Xmx64m" "-Xms64m" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
