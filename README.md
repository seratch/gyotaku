# Gyotaku - 魚拓（ぎょたく）

## What's this?

Gyotaku is a simple tool to completely save web pages.

## Requirement

- Mac OS/Linux/Windows
- Java Runtime Environment
- Firefox

## Basic Usage

### Get Gyotaku

Download gyotaku.zip and unzip it.

https://github.com/seratch/gyotaku/downloads


## Invoke Gyotaku

Using Gyotaku UI (Swing Application) is the easiest way.

```
./gyotaku_ui
````

![screen_shot](https://github.com/seratch/gyotaku/raw/master/img/gyotaku_screen_shot.png)


## Authentication

If you want to get a page which requires authentication, use the selenium web driver which is customized by yourself.

### input/tumblr-login.scala

Added the following source code:

```scala
import org.openqa.selenium._
val driver = new firefox.FirefoxDriver
driver.get("https://www.tumblr.com/login")
driver.findElement(By.id("signup_email")).sendKeys("YOUR_EMAIL")
driver.findElement(By.id("signup_password")).sendKeys("YOUR_PASSWORD")
driver.findElement(By.id("signup_form")).submit()
driver
```

### input/tumblr.yml

```yml
name: tumblr-dashbord
url: http://www.tumblr.com/dashboard
driver: { path: input/tumblr-login.scala }
```


## Configuration

### name

The name of gyotaku. It'll be used as directory name under output directory.

### url

The url to download.

### driver

How to create a `org.openqa.selenium.WebDriver` instance. 

`FirefoxDriver` will be used if it's omitted.

```yml
driver
  path: path/to/driver.scala
```

or

```json
"driver" { 
  "source": "import org.openqa.selenium._
   val drvier = new firefix.FirefoxDriver
   // do something
   driver
  " 
}
```

### charset

Charset which is used for the downloaded html and modified css files. 

"UTF-8" if it's omitted.

### prettify

Modify the html using HtmlCleaner or not. 

`false` if it's omitted.

### replaceNoDomainOnly

Replace urls in html/css only when they don't start with 'http://' or 'https://'.

`true` if it's omitted.
 

