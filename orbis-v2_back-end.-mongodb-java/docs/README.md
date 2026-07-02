# Postman collection usage

To use Postman collection it's needed first to import it into postman and setup environment variables.
It can be done either globally or in separate environment.

```
baseUrl=https://orbis-v2.rj.r.appspot.com
email=<your-email>
username=<desired username>
password=<desired password>
```

Email should be valid email as upon calling signup it will be sent to Firebase auth alongside with the password
and firebase will be responsible for further password checks.

First call after setup needs to be `signup`. You just need to press `Send` once with `signup` request open.
If registration will be successful Postman will execute post-request script, extract the token received from 
the Firebase, and store it in the session variables. The token is valid for 2 hours, so if application will start 
giving 401 or 403 status codes in response to the requests all what needs to be done is to open `login` request and 
press `Send` button. Just like with signup PostMan will extract token and store it for future use.

# Open for all requests

```
GET /places?[longitude=<>&latitude=<>&page=0..&limit=20]
GET /places/:placeKey
GET /groups?[longitude=<>&latitude=<>&page=0..&limit=20]
```

# Secured requests

```
POST /places
PUT /places/:placeKey

POST /profile/me
GET /profile/me
GET /profile/:userKey
```
