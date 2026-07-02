db.getCollection('users').aggregate([
    {$set: {memberCountUser: {$size: {$ifNull: ["$member", []]}}}},
    {$lookup: { from: "groups", let: {"uid": "$userKey"}, pipeline: [
                {$match: {$expr: {$in: ["$$uid", "$members"]}}},
                {$group: {_id: "cnt", "cnt": {$sum: 1}} }
            ], as: "memberCountGroup" }},
    {$unwind: {path: "$memberCountGroup", preserveNullAndEmptyArrays: true}},
    {$set: {memberCountGroup: {$ifNull: ["$memberCountGroup.cnt", 0]}}},
    {$match: {$expr: {$ne:["$memberCountGroup", "$memberCountUser"]}}},
    {$project: {userKey: "$userKey", memberCountUser: "$memberCountUser", memberCountGroup: "$memberCountGroup"}}
])

db.getCollection('users').aggregate([
    {$lookup: { from: "groups", let: {"uid": "$userKey"}, pipeline: [
                {$match: {$expr: {$in: ["$$uid", "$members"]}}},
                {$project: {groupKey: "$groupKey"} }
            ], as: "member" }},
    {$set: {member: {$map: {input: "$member", in: "$$this.groupKey"}}}},
    {$merge: "users"}
])
