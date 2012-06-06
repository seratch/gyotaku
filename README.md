# gyotaku

## What's this?

A tool to save web pages.

## Requirement

- Mac OS/Linux/Windows
- Java Runtime Environment
- Firefox

## Basic Usage

### Get gyotaku

Download gyotaku.zip and unzip it.

### Prepare config json

```sh
mkdir input
vim input/example.json
```

Create a JSON config file:

```json
{ "name": "example",
  "url": "http://example.com/" }
```

### Run gyotaku

```sh
$ ./gyotaku input/example.json output
Target: http://www.example.com/
Now downloading..................Finished.
$
```

The content will be stored in `./output`.


## How to download pages that require authentication

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

### input/tumblr.json

```json
{ "name": "tumblr-dashbord",
  "url": "http://www.tumblr.com/dashboard",
  "driver": { "path": "input/tumblr-login.scala" } }
```

### run gyotaku

```sh
./gyotaku input/tumblr.json output
```

## Configuration

### name

The name of gyotaku. It'll be used as directory name under output directory.

### url

The url to download.

### driver

How to create a `org.openqa.selenium.WebDriver` instance. 

`FirefoxDriver` will be used if it's omitted.

```json
"driver" { "path": "path/to/driver.scala" }
```

or

```json
"driver" { "source": "new org.openqa.selenium.firefix.FirefoxDriver" }
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
 


