# lostcrmanager REST API Documentation

> **Bot**: Clash Royale Discord manager (`lostcrmanager`)  
> **Base URL**: `http://localhost:{REST_API_PORT}` (configured via `REST_API_PORT` env var)  
> **Auth env var**: `REST_API_TOKEN`

---

## Authentication

All endpoints (except `OPTIONS`) require an API token when `REST_API_TOKEN` is set in the environment.

**Supply the token via either header:**

| Header | Example |
|---|---|
| `Authorization` | `Bearer your_token_here` |
| `X-API-Token` | `your_token_here` |

If the environment variable is not set, all requests are allowed without a token.

---

## Common Response Shapes

### Error response
```json
{
  "error": "Description of what went wrong"
}
```

### Management endpoint error response
```json
{
  "success": false,
  "error": "Description of what went wrong"
}
```

### Management endpoint success response
```json
{
  "success": true,
  "message": "Human-readable confirmation"
}
```

---

## Data Types

### ClanDTO
```json
{
  "tag": "#2RRR",
  "index": 1,
  "nameDB": "LOST CR",
  "description": "Main LOST CR clan",
  "maxKickpoints": 10,
  "kickpointsExpireAfterDays": 30,
  "kickpointReasons": [
    {
      "name": "Inactive",
      "clanTag": "#2RRR",
      "amount": 2
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `tag` | string | Clan tag (e.g. `#2RRR`) |
| `index` | number \| null | Display sort order |
| `nameDB` | string \| null | Clan name stored in DB |
| `description` | string \| null | Clan description from DB |
| `maxKickpoints` | number \| null | Kickpoints before kick (configured via `clanconfig`) |
| `kickpointsExpireAfterDays` | number \| null | Days until a kickpoint expires |
| `kickpointReasons` | KickpointReasonDTO[] \| null | Configured kickpoint reason presets |

> **Note**: Unlike the CoC API, lostcrmanager ClanDTO does not include `badgeUrl` or `minSeasonWins`.

### KickpointReasonDTO
```json
{
  "name": "Inactive",
  "clanTag": "#2RRR",
  "amount": 2
}
```

### KickpointDTO
```json
{
  "id": 42,
  "description": "Inactive",
  "amount": 2,
  "date": "2024-01-15T00:00:00+01:00",
  "givenDate": "2024-01-15T00:00:00+01:00",
  "expirationDate": "2024-02-14T00:00:00+01:00",
  "givenByUserId": "987654321012345678"
}
```

| Field | Type | Description |
|---|---|---|
| `id` | number \| null | Unique kickpoint ID |
| `description` | string \| null | Reason text |
| `amount` | number \| null | Kickpoint value |
| `date` | string \| null | Reference date (ISO-8601) |
| `givenDate` | string \| null | Date the record was created (ISO-8601) |
| `expirationDate` | string \| null | Date the kickpoint expires (ISO-8601) |
| `givenByUserId` | string \| null | Discord user ID of the person who added it |

### PlayerDTO
```json
{
  "tag": "#CRPLAYER",
  "nameDB": "PlayerName",
  "userId": "123456789012345678",
  "roleInClan": "COLEADER",
  "isHidden": false,
  "clanDB": { "...ClanDTO..." },
  "totalKickpoints": 3,
  "activeKickpoints": [
    { "...KickpointDTO..." }
  ],
  "monthlyWins": 12,
  "monthlyWinsHasWarning": false
}
```

| Field | Type | Description |
|---|---|---|
| `tag` | string | Player tag |
| `nameDB` | string \| null | Player name stored in DB |
| `userId` | string \| null | Linked Discord user ID (`null` if unlinked) |
| `roleInClan` | string | `LEADER`, `COLEADER`, `ELDER`, `MEMBER`, or `NOTINCLAN` |
| `isHidden` | boolean \| null | True if the player is a hidden co-leader |
| `clanDB` | ClanDTO \| null | The clan the player belongs to in DB. `null` if the player is on the waitlist or not in any clan. |
| `totalKickpoints` | number \| null | Sum of all active kickpoint amounts |
| `activeKickpoints` | KickpointDTO[] \| null | Individual non-expired kickpoints |
| `monthlyWins` | number \| null | Current-month CR season wins |
| `monthlyWinsHasWarning` | boolean \| null | True if wins are below expectations |

> **Note on roles**: DB stores `"leader"`, `"coleader"`, `"hiddencoleader"`, `"elder"`, `"member"`. These map to the enum names shown in `roleInClan`.

### UserDTO
```json
{
  "isAdmin": false,
  "linkedPlayers": ["#CRPLAYER1", "#CRPLAYER2"],
  "highestRole": "COLEADER",
  "nickname": "Username"
}
```

| Field | Type | Description |
|---|---|---|
| `isAdmin` | boolean | True if the user has Discord admin role |
| `linkedPlayers` | string[] | List of linked CR player tags |
| `highestRole` | string \| null | Highest role across all clans: `LEADER`, `COLEADER`, `ELDER`, `MEMBER`, or `NOTINCLAN` |
| `nickname` | string \| null | Discord server nickname |

---

## Read Endpoints

### GET /api/clans

Returns all registered clans.

**Request**: No body required.

**Response** `200 OK`:
```json
[
  {
    "tag": "#2RRR",
    "index": 1,
    "nameDB": "LOST CR",
    "description": "Main CR clan",
    "maxKickpoints": 10,
    "kickpointsExpireAfterDays": 30,
    "kickpointReasons": [
      { "name": "Inactive", "clanTag": "#2RRR", "amount": 2 }
    ]
  }
]
```

---

### GET /api/clans/{tag}

Returns a single clan by tag.

**Path param**: `tag` — e.g. `%232RRR` (URL-encoded `#2RRR`)

**Response** `200 OK` — single ClanDTO  
**Response** `404 Not Found`:
```json
{ "error": "Clan not found" }
```

**Example response**:
```json
{
  "tag": "#2RRR",
  "index": 1,
  "nameDB": "LOST CR",
  "description": "Main LOST CR clan",
  "maxKickpoints": 10,
  "kickpointsExpireAfterDays": 30,
  "kickpointReasons": [
    { "name": "Inactive",      "clanTag": "#2RRR", "amount": 2 },
    { "name": "No Participation", "clanTag": "#2RRR", "amount": 1 }
  ]
}
```

---

### GET /api/clans/{tag}/members

Returns all members of a clan.

**Path param**: `tag` — clan tag

**Response** `200 OK` — array of PlayerDTO  
**Response** `404 Not Found`:
```json
{ "error": "Clan not found" }
```

---

### GET /api/players/{tag}

Returns a single player by CR tag.

**Path param**: `tag` — player tag (URL-encode `#` as `%23`)

**Response** `200 OK` — PlayerDTO  
**Response** `404 Not Found`:
```json
{ "error": "Player not found" }
```

**Example request**:
```
GET /api/players/%23CRPLAYER
```

**Example response**:
```json
{
  "tag": "#CRPLAYER",
  "nameDB": "SomeName",
  "userId": "123456789012345678",
  "roleInClan": "MEMBER",
  "isHidden": false,
  "clanDB": {
    "tag": "#2RRR",
    "index": 1,
    "nameDB": "LOST CR",
    "description": "Main LOST CR clan",
    "maxKickpoints": 10,
    "kickpointsExpireAfterDays": 30,
    "kickpointReasons": []
  },
  "totalKickpoints": 0,
  "activeKickpoints": null,
  "monthlyWins": 12,
  "monthlyWinsHasWarning": false
}
```

---

### GET /api/users/{userId}

Returns data about a Discord user.

**Path param**: `userId` — Discord user ID (18-digit snowflake)

**Response** `200 OK` — UserDTO  
**Response** `404 Not Found`:
```json
{ "error": "User not found" }
```

**Example request**:
```
GET /api/users/123456789012345678
```

**Example response**:
```json
{
  "isAdmin": false,
  "linkedPlayers": ["#CRPLAYER1"],
  "highestRole": "MEMBER",
  "nickname": "SomeUser"
}
```

---

### GET /api/coleaders

Returns a list of Discord users who hold a Leader or Co-Leader role in any registered clan, along with their highest-level linked account that is currently in a clan.

**Response** `200 OK`:
```json
[
  {
    "userId": "123456789012345678",
    "highestAccountTag": "#CRPLAYER",
    "highestAccountName": "PlayerName"
  }
]
```

| Field | Type | Description |
|---|---|---|
| `userId` | string | Discord user ID |
| `highestAccountTag` | string | Tag of the highest-level linked account that is in a clan |
| `highestAccountName` | string | Name of that account (from CR API) |

Users with no linked accounts currently in a clan are excluded from the response.

**Example response**:
```json
[
  {
    "userId": "123456789012345678",
    "highestAccountTag": "#CRPLAYER1",
    "highestAccountName": "TopPlayer"
  },
  {
    "userId": "987654321098765432",
    "highestAccountTag": "#CRPLAYER2",
    "highestAccountName": "OtherLeader"
  }
]
```

---

## Management Endpoints

All management endpoints:
- Use `POST` method
- Require a JSON body with `Content-Type: application/json`
- Require valid authentication
- Always include `"discordUserId"` — the Discord user ID of the caller, used to verify permissions

### Special clan tag: `warteliste`

`"warteliste"` is a reserved clan tag representing a waiting list. It has relaxed permission rules for some endpoints (any co-leader or higher, rather than co-leader in that specific clan), and certain operations (kickpoints, clanconfig) are not allowed for waitlist members.

### Permission levels (in order of hierarchy)

| Level | Description |
|---|---|
| **admin** | User has the Discord server admin role |
| **leader** | User has a player with role `leader` in the target clan |
| **coleader or higher in clan** | User has a player with role `coleader`, `hiddencoleader`, `leader`, or is admin — in the specified clan |
| **coleader or higher (any clan)** | Same as above but in any registered clan |

---

### POST /api/manage/members/add

Adds a linked player to a clan in the database and assigns the Discord member role.

**Required permission**: Co-leader or higher in the target clan (or any clan if adding to `warteliste`).  
**Extra permission**: Leader or admin required to assign `leader`, `coleader`, or `hiddencoleader` roles.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#CRPLAYER",
  "clanTag": "#2RRR",
  "role": "member"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `playerTag` | string | ✓ | CR tag of the player to add |
| `clanTag` | string | ✓ | Tag of the destination clan (or `"warteliste"`) |
| `role` | string | ✓ | One of: `leader`, `coleader`, `hiddencoleader`, `elder`, `member` |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Player added to clan",
  "roleChanges": [
    { "type": "added", "roleId": "111222333444555666", "userId": "123456789012345678" }
  ]
}
```

`roleChanges` lists Discord roles that were assigned. An empty array means no role changes were needed. No role changes are performed when adding to `warteliste`.

**Error responses**:

| Status | Condition |
|---|---|
| `400` | Missing fields \| invalid role \| player already in a clan |
| `403` | Insufficient permissions |
| `404` | Clan not found \| player not linked |

---

### POST /api/manage/members/edit

Changes a member's role within their current clan. Cannot edit waitlist members.

**Required permission**: Co-leader or higher in the member's current clan.  
**Extra permission**: Leader or admin required to assign/change `leader`, `coleader`, or `hiddencoleader` roles.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#CRPLAYER",
  "role": "elder"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `playerTag` | string | ✓ | Player tag |
| `role` | string | ✓ | New role: `leader`, `coleader`, `hiddencoleader`, `elder`, `member` |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Member role updated"
}
```

**Error** `400` if the player is on the waitlist.

---

### POST /api/manage/members/remove

Removes a player from their clan in the database. For non-waitlist clans, reports the current Discord member role status.

**Required permission**: Co-leader or higher in the member's clan (or any clan if removing from `warteliste`).  
**Extra permission**: Leader or admin required to remove a `leader` or `coleader`.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#CRPLAYER"
}
```

**Response** `200 OK` (non-waitlist):
```json
{
  "success": true,
  "message": "Member removed from clan",
  "memberRoleStatus": "still_has_role",
  "memberRoleId": "111222333444555666"
}
```

`memberRoleStatus` is either `"still_has_role"` or `"no_role"`. The caller is responsible for removing the Discord role; the API only reports the current state.  
`memberRoleStatus` and `memberRoleId` are omitted when removing from `warteliste`.

---

### POST /api/manage/members/transfer

Transfers a player from one clan to another (including to/from `warteliste`). The player's role is reset to `member` in the destination clan. Discord member roles for both clans are updated automatically.

**Required permission**: Co-leader or higher in both the source and destination clans (or any clan if either is `warteliste`).  
**Extra permission**: Leader or admin required to transfer a `leader` or `coleader`.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#CRPLAYER",
  "newClanTag": "#OTHERCLAN"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `playerTag` | string | ✓ | Player tag |
| `newClanTag` | string | ✓ | Tag of the destination clan (or `"warteliste"`) |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Player transferred",
  "roleChanges": [
    { "type": "removed", "roleId": "111222333444555666", "userId": "123456789012345678" },
    { "type": "added",   "roleId": "222333444555666777", "userId": "123456789012345678" }
  ]
}
```

`roleChanges` lists Discord role additions/removals. Roles are not touched when the source or destination is `warteliste`.

**Error** `400` if source and destination clans are the same.

---

### POST /api/manage/kickpoints/add

Adds a kickpoint entry for a player. Player must be in a non-waitlist clan and the clan must have a config set.

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#CRPLAYER",
  "reason": "Inactive",
  "amount": 2,
  "date": "15.01.2024"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `playerTag` | string | ✓ | Player tag |
| `reason` | string | ✓ | Description/reason for the kickpoint |
| `amount` | number | ✓ | Kickpoint value |
| `date` | string | – | Date in `dd.MM.yyyy` format. Defaults to today if omitted. |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint added",
  "kickpointId": 42,
  "warnings": [
    "Kickpoint is already expired based on the given date",
    "Player has reached maximum kickpoints"
  ]
}
```

`warnings` is only present when there are warnings. Possible warnings:
- `"Kickpoint is already expired based on the given date"` — the provided date plus expiry is in the past
- `"Player has reached maximum kickpoints"` — player is at or above the clan's configured maximum

**Error responses**:

| Status | Condition |
|---|---|
| `400` | Missing fields \| invalid date format \| `warteliste` player \| clan config not set |
| `403` | Insufficient permissions |
| `404` | Player not in a clan |

---

### POST /api/manage/kickpoints/edit

Edits an existing kickpoint entry.

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "id": 42,
  "reason": "Updated Reason",
  "amount": 3,
  "date": "16.01.2024"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `id` | number | ✓ | Kickpoint ID |
| `reason` | string | ✓ | New description |
| `amount` | number | ✓ | New kickpoint value |
| `date` | string | ✓ | New date in `dd.MM.yyyy` format |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint updated"
}
```

---

### POST /api/manage/kickpoints/remove

Deletes a kickpoint entry. Player must be in a non-waitlist clan.

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "id": 42
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint removed"
}
```

**Error** `400` if the player is on the waitlist.  
**Error** `404` if the kickpoint ID doesn't exist.

---

### POST /api/manage/clanconfig

Sets or updates the kickpoint configuration for a clan. Cannot configure `warteliste`.

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "clanTag": "#2RRR",
  "maxKickpoints": 10,
  "kickpointsExpireAfterDays": 30
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `clanTag` | string | ✓ | Clan tag to configure |
| `maxKickpoints` | number | ✓ | Number of kickpoints before a member should be kicked |
| `kickpointsExpireAfterDays` | number | ✓ | Days until kickpoints expire |

> **Note**: Unlike lostmanager, `minSeasonWins` is not part of the CR clan config.

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Clan config updated"
}
```

---

### POST /api/manage/kickpoint-reasons/add

Adds a new kickpoint reason preset for a clan. Cannot add reasons for `warteliste`.

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "clanTag": "#2RRR",
  "reason": "Inactive",
  "amount": 2,
  "index": 1
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `clanTag` | string | ✓ | Clan tag |
| `reason` | string | ✓ | Reason name (must be unique per clan) |
| `amount` | number | ✓ | Default kickpoint value |
| `index` | number | – | Display sort order. Auto-assigned (max+1) if omitted. |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint reason added",
  "index": 1
}
```

**Error** `400` if the reason already exists for that clan.

---

### POST /api/manage/kickpoint-reasons/edit

Updates an existing kickpoint reason preset. Cannot edit reasons for `warteliste`.

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "clanTag": "#2RRR",
  "reason": "Inactive",
  "amount": 3,
  "index": 2
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `clanTag` | string | ✓ | Clan tag |
| `reason` | string | ✓ | Existing reason name to edit |
| `amount` | number | ✓ | New kickpoint value |
| `index` | number | – | New sort order (optional) |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint reason updated"
}
```

---

### POST /api/manage/kickpoint-reasons/remove

Removes a kickpoint reason preset. Cannot remove reasons for `warteliste`.

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "clanTag": "#2RRR",
  "reason": "Inactive"
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint reason removed"
}
```

**Error** `404` if the reason doesn't exist.

---

### POST /api/manage/links/link

Links a CR player to a Discord user (first-time link). Also triggers a background wins save.

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#CRPLAYER",
  "targetUserId": "987654321098765432"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `playerTag` | string | ✓ | CR player tag. `#` is prepended if missing. |
| `targetUserId` | string | ✓ | Discord user ID to link the player to |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Player linked to user"
}
```

**Error** `400` if the player is already linked — use `relink` instead.  
**Error** `404` if the player doesn't exist in the CR API.

---

### POST /api/manage/links/relink

Changes the Discord user a CR player is linked to. Also triggers a background wins save.

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#CRPLAYER",
  "targetUserId": "111222333444555666"
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Player relinked to user"
}
```

**Error** `400` if the player is not yet linked — use `link` instead.

---

### POST /api/manage/links/unlink

Removes a player's link (deletes from DB).

**Required permission**: Co-leader or higher in any clan.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "playerTag": "#CRPLAYER"
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Player unlinked"
}
```

**Error** `404` if the player is not linked.

> **Note**: Unlike lostmanager, there is no clan membership check before unlinking.

---

### POST /api/manage/copyreasons

Copies all kickpoint reason presets from one source clan to every other registered clan, completely replacing their existing reasons.

**Required permission**: Admin only.

**Request body**:
```json
{
  "discordUserId": "123456789012345678",
  "sourceClanTag": "#2RRR"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `discordUserId` | string | ✓ | Caller's Discord user ID |
| `sourceClanTag` | string | ✓ | Tag of the clan whose reasons to copy from |

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Kickpoint reasons copied to 3 clans",
  "reasonsCopied": 4,
  "clansUpdated": 3
}
```

| Field | Description |
|---|---|
| `reasonsCopied` | Number of reason entries copied per clan |
| `clansUpdated` | Number of clans that received the new reasons |

**Error** `400` if the source clan has no reasons or is `warteliste`.  
**Error** `404` if the source clan is not found.

---

### POST /api/manage/restart

Initiates a bot restart after a 3-second delay (`System.exit(0)`).

**Required permission**: Admin only.

**Request body**:
```json
{
  "discordUserId": "123456789012345678"
}
```

**Response** `200 OK` (returned before the process exits):
```json
{
  "success": true,
  "message": "Bot restart initiated"
}
```

---

## HTTP Status Code Summary

| Code | Meaning |
|---|---|
| `200` | Success |
| `204` | CORS preflight (OPTIONS) |
| `400` | Bad request (missing/invalid fields, business rule violation) |
| `401` | Missing or invalid API token |
| `403` | Insufficient permissions |
| `404` | Resource not found |
| `405` | Wrong HTTP method |
| `500` | Internal server error |
