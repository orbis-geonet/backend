## 15.08.2023
## Add new subscriptions + purchases 

1. Added new subscriptions: type 
Group admin can create subscriptions:
   UNLIMITED - unlimited (old one),
   INTERVAL - with time limit. For example only for 3 month. In this case Group admin must add period (for example 3 as 3 months )
   ONE_TIME - one time purchase. Not subscription. User can buy it ass purchase 

2. Added interval (only for UNLIMITED and INTERVAL):
MONTH - user pay every month
YEAR - user pay every year 

3. To buy ONE_TIME type user must use new API:
   POST /profile/purchase/{{purchaseKey}}/buy
user can buy more than 1 purchase. set number to buy more than 1 (1 is default).

user can get information about the bought purchases:
GET /profile/purchases

group admin can get information about group bought purchases:
GET /groups/purchases

