# Backend Design Link Shortener
_The backend project is to design, plan, and estimate building a simple link shortening service (think very simple bit.ly)_

## Requirements
### Functional Requirements:
There is no user interface for the link shortening service.  There must be a REST API to create, retrieve, update, and delete shortened URLs.  Customers are given an API token for use with the service.

For this example, the link shortening service will have the domain `lin.ks`

There are a few rules for shortened URLs:
1. The full URL provided by the customer (e.g. https://www.google.com) will always be shortened to an encoded value with our domain (e.g. https://lin.ks/xCd5a)
2. Shortened URLs must be unique for each customer.  If two different customers create a short URL to the same destination (customer A and customer B both create short URLs to https://www.google.com), each customer is given a unique shortened URL
3. Duplicate shortened links for each customer are not allowed.  If a customer attempts to create a new shortened link for a URL that already exists, the existing shortened link will be provided (e.g. a link for https://www.google.com already exists.  Customer tries to create a new link to the same place, we return the existing short URL)
### Non-Functional Requirements:
1. The system should be highly available & scalable
2. URL redirection should happen in real-time with minimal latency
3. The application should be durable and fault-tolerant   
4. Shortened links should not be predictable
### Extended Requirements:
The link shortening service will also provide an API to report usage for each shortened link.  The API must provide access to the following reports:
1. Total number of clicks
2. Number of clicks by day
3. Total number of people who clicked links
4. Total number of people who clicked by day

## Capacity Estimation
### Traffic estimates:
Projected to have 1,000 daily active users, each user creating 5 to 10 short URLs per day, so number of unique shortened links generated per day between 5,000 to 10,000 and 3 to 7 URLs every minute
```
5000 / 60 * 24 is around 3 links / minute 
10,000 / 60 * 24 is around 7 links / minute
```
Predicted daily volume of 100,000 daily visitors clicking the short URL links.
```
100,000 / 60 * 60 * 24 is around 1 click every second
```
### Storage estimates: 
Assuming lifetime of service to be 10 years and with 10,000 shortened links creation per day, total number of data points in system will be = 10,000/day * 365 (days) * 10 (years) = 36 million

Let’s assume that each stored object will be approximately 500 bytes. We will need 18GB of total storage = 36 million * 500 bytes = 18 GB

We need to think about the reads and writes that will happen on our system for this amount of data. This will decide what kind of database (RDBMS or NoSQL) we need to use.
### Bandwidth estimates: 
For write requests, since we expect 3 to 7 new URLs every minute, total incoming data for our service will be 1.5KB to 3.5KB per minute:

(3, 7) * 500 bytes ~ 1.5 to 3.5 KB/minute
For read requests, since every second we expect ~1 URL redirections, total outgoing data for our service would be 500 bytes per second:

1 * 500 bytes = ~500 bytes/s
### Memory estimates: 
If we want to cache some of the hot URLs that are frequently accessed, how much memory will we need to store them? If we follow the 80-20 rule, meaning 20% of URLs generate 80% of traffic, we would like to cache these 20% hot URLs.

Since we have ~1 request per second, we will be getting ~86,400 requests per day:

1 * 3600 seconds * 24 hours = ~86,400

To cache 20% of these requests, we will need 8.6MB of memory.

0.2 * 86,400 * 500 bytes = ~8.6MB

One thing to note here is that since there will be many duplicate requests (of the same URL), our actual memory usage will be less than 8.6MB.

## High Level Design
![alt text](resources/Link Shortener.png?raw=true)
* Aurora database to store the mapping of short URLs and original URLs
* ElasticCache - use LRU cache to keep the most active short URLs  
* Amazon Kinesis stream - streaming the click stream events
* Lambda function to take click stream event from Kinesis streams and insert data into Amazon Timestream
* Amazon Timestream - This is time series database to store the click events. 
## APIs
As agreed in the requirements, we will have the below APIs:
### Shorten long URL - POST /urls
```json
{
  "longUrl": "string"
}
```
Backend server will perform validation on the length of the long URL. Accordingly, it will return the following response codes:-
* 400- Bad request
* 201- Created. Url mapping entry added
* 200- OK. Idempotent response for the duplicate request
### Fetch long Url from short URL - GET /urls/{short_url}
If the system finds the long URL corresponding to the short URL, the user will be redirected. The HTTP response code will be 302.

The service will throw a 404 (Not Found) in case the short URL is absent. It will perform a length check on the input. In case, the length exceeds the threshold, 400 (Bad Request) will be thrown.
### Dockerfile
We will use the docker image to pack the apis and deploy them into Kubernetes environment
```yaml
FROM python:3.8-slim-buster

WORKDIR /app

COPY requirements.txt requirements.txt
RUN pip3 install -r requirements.txt

COPY ../deploy .

CMD [ "python3", "-m" , "flask", "run", "--host=0.0.0.0"]
```
### Kubernetes deployment
We use HelmChart to deploy the kubernetes deployment yaml file
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: url-service
  namespace: {{ .Release.Namespace }}
spec:
  selector:
    matchLabels:
      app: url-service
  replicas: {{ .Values.replicaCount }}
  template:
    metadata:
      labels:
        app: url-service
    spec:
      containers:
        - name: user-service
          image: "shortener/url-service:{{ .Values.urlService.version }}"
          imagePullPolicy: {{ .Values.urlService.pullPolicy }}
          env:
            - name: NODE_ENV
              value: "{{ .Values.urlService.nodeEnv }}"
          ports:
            - containerPort: 4000
```
### CI/CD pipelines
![alt text](resources/Kubernetes CI_CD.png?raw=true)
* Github Actions is for CI pipeline to release helm chart to Chart Museum
```yaml
name: GitHub Actions
on: [push]
jobs:
  GitHub-Actions:
    runs-on: ubuntu-latest
    steps:
      # Install Helm
      curl https://baltocdn.com/helm/signing.asc | sudo apt-key add -
      sudo apt-get install apt-transport-https --yes
      echo "deb https://baltocdn.com/helm/stable/debian/ all main" | sudo tee /etc/apt/sources.list.d/helm-stable-debian.list
      sudo apt-get update
      sudo apt-get install helm
      helm plugin install https://github.com/chartmuseum/helm-push.git
      # Push Helmchart to repo
      cd charts
      helm repo add chartmuseum https://chartmuseum.com/
      helm push url_service/ chartmuseum
```
* Chart Museum: Chart repository
* FluxCD: is the CD pipeline to deploy helmchart and container images to Kubernetes

## Database Design
### Database Schema:
```
CREATE TABLE ShortenerUrl (
    Id  BIGINT                 NOT NULL,  AUTO_INCREMENT
    ShortUrl  VARCHAR(7)       NOT NULL,
    OriginalUrl  VARCHAR(400)  NOT NULL,
    CreationDate  datetime     NOT NULL,
    UserId   VARCHAR(50)       NOT NULL,
    automatically on primary-key column
    
    INDEX (ShortUrl)
    INDEX (OriginalUrl)
);

CREATE TABLE User (
    Id  BIGINT                 NOT NULL,  AUTO_INCREMENT
    Name  VARCHAR(20)          NOT NULL,
    Email VARCHAR(32)          NOT NULL,
    CreationDate  datetime     NOT NULL,
    LastLogin   datetime       NULL,
    automatically on primary-key column
);
```
**What kind of database should we use?** Since we anticipate storing billions of rows, and we don’t need to use relationships between objects – a NoSQL store like DynamoDB, Cassandra or Riak is a better choice. A NoSQL choice would also be easier to scale. Please see SQL vs NoSQL for more details.
## Encoding Algorithm
* Take the [**MD5**](https://en.wikipedia.org/wiki/MD5) hash of the user's ip_address + timestamp
    * MD5 is a widely used hashing function that produces a 128-bit hash value
    * MD5 is uniformly distributed
    * Alternatively, we could also take the MD5 hash of randomly-generated data
* [**Base 62**](https://www.kerstner.at/2012/07/shortening-strings-using-base-62-encoding/) encode the MD5 hash
    * Base 62 encodes to `[a-zA-Z0-9]` which works well for urls, eliminating the need for escaping special characters
    * There is only one hash result for the original input and Base 62 is deterministic (no randomness involved)
    * Base 64 is another popular encoding but provides issues for urls because of the additional `+` and `/` characters
    * The following Base 62 pseudocode runs in O(k) time where k is the number of digits = 5:

```python
def base_encode(num, base=62):
    digits = []
    while num > 0:
        remainder = num % base
        digits.push(remainder)
        num = num / base
    digits = digits.reverse
```

* Take the first 5 characters of the output, which results in 62^5 possible values and should be sufficient to handle our constraint of 36 million shortlinks in 10 years:

```python
url = base_encode(md5(ip_address+timestamp))[:URL_LENGTH]
```

## Cache
We can cache URLs that are frequently accessed by the users. The UGS servers, before making a query to the database, may check if the cache has the desired URL. Then it does not need to make the query again.
What will happen when the cache is full? We may replace an older not used link with a newer or popular URL. We may choose the Least Recently Used (LRU) cache eviction policy for our system. In this policy, we remove the least recently used URL first.

LRU cache implementation
```python
from collections import OrderedDict

class LRUCache:

    # initialising capacity
    def __init__(self, capacity: int):
        self.cache = OrderedDict()
        self.capacity = capacity

    # we return the value of the key
    # that is queried in O(1) and return -1 if we
    # don't find the key in out dict / cache.
    # And also move the key to the end
    # to show that it was recently used.
    def get(self, key: int) -> int:
        if key not in self.cache:
            return -1
        else:
            self.cache.move_to_end(key)
            return self.cache[key]

    # first, we add / update the key by conventional methods.
    # And also move the key to the end to show that it was recently used.
    # But here we will also check whether the length of our
    # ordered dictionary has exceeded our capacity,
    # If so we remove the first key (least recently used)
    def put(self, key: int, value: int) -> None:
        self.cache[key] = value
        self.cache.move_to_end(key)
        if len(self.cache) > self.capacity:
            self.cache.popitem(last = False)

```
## Load balancer
We can add a load balancing layer at different places in our system, in front of the URL shortening server, database, and cache servers.
We may use a simple Round Robin approach for request distribution. In this approach, LB distributes incoming requests equally among backend servers. This approach of LB is simple to implement. If a server is dead, LB will stop sending any traffic to it.
Problem: If a server is overloaded, the LB will not stop sending a new request to that server in this approach. We might need an intelligent LB later.

## Telemetry
### Amazon Timestream example data
click_time | user_ip | short_url | web_browser
--- | --- | --- | ---
2021-08-19 19:00:00.000000000 | 222.155.60.232 | https://lin.ks/xCd5a | Chrome 
2021-08-19 08:00:00.000000000 | 222.155.60.232 | https://lin.ks/xCd5a | Chrome
2021-08-15 21:00:00.000000000 | 222.155.60.232 | https://lin.ks/xCd5a | Chrome
2021-08-13 06:00:00.000000000 | 222.222.34.564 | https://lin.ks/cdFg2 | Firefox
2021-08-13 11:00:00.000000000 | 222.222.34.564 | https://lin.ks/cdFg2 | Firefox

1. Total number of clicks
```sql
SELECT
    count(click_time)
FROM ClickStream
```
2. Number of clicks by day
```sql
SELECT
    count(click_time)
FROM ClickStream
GROUP BY date_trunc(day, click_time)
```
3. Total number of people who clicked links
```sql
SELECT
    count(user_ip)
FROM ClickStream
GROUP BY user_ip
```
4. Total number of people who clicked by day
```sql
SELECT
    count(user_ip),
    date_trunc(day, click_time) as click_day
FROM ClickStream
GROUP BY user_ip, date_trunc(day, click_time)
```

## Security
We can store the access type (public/private) with each URL in the database. If a user tries to access a URL, which he does not have permission, the system can send an error (HTTP 401) back.

