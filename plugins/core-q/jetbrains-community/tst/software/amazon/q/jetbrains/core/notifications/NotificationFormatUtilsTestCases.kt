// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.notifications

val validComputeInvalidOs = """{
    "id": "example_id_12344",
    "schedule": {
    "type": "StartUp"
},
    "severity": "Critical",
    "condition": {
    "compute": {
    "type": {"==": "Local"}
},
"os":  {
    "type": {"==": "Windows"}
}
},
    "actions": [
    {
        "type": "ShowMarketplace",
        "content": {
        "en-US": {
        "title": "Go to market"
    }
    }
    }
    ],
    "content": {
    "en-US": {
    "title": "Look at this!",
    "description": "Some bug is there"
}
}
}
""".trimIndent()

val validOsInvalidComputeData = NotificationData(
    id = "example_id_12344",
    schedule = NotificationSchedule(type = "StartUp"),
    severity = "Critical",
    condition = NotificationDisplayCondition(
        compute = ComputeType(type = NotificationExpression.ComparisonCondition("Local"), architecture = null),
        os = SystemType(type = NotificationExpression.ComparisonCondition("Windows"), version = null),
        ide = null,
        extension = null,
        authx = null
    ),
    actions = listOf(
        NotificationFollowupActions(
            type = "ShowMarketplace",
            content = NotificationFollowupActionsContent(
                NotificationActionDescription(
                    title = "Go to market",
                    url = null
                )
            )
        )
    ),
    content = NotificationContentDescriptionLocale(
        NotificationContentDescription(
            title = "Look at this!",
            description = "Some bug is there"
        )
    )
)

val invalidExtensionVersion = """{
    "id": "example_id_12344",
    "schedule": {
    "type": "StartUp"
},
    "severity": "Critical",
    "condition": {
   "extension": [
             {
                "id": "aws.toolkit",
                "version": {
                    "!=": "1.3334"
                }
            },
        {
            "id": "amazon.q",
            "version": {
            ">": "3.37.0"
        }
        }
        ]
},
    "content": {
    "en-US": {
    "title": "Look at this!",
    "description": "Some bug is there"
}
}
}
""".trimIndent()

val invalidExtensionVersionData = NotificationData(
    id = "example_id_12344",
    schedule = NotificationSchedule(type = "StartUp"),
    severity = "Critical",
    condition = NotificationDisplayCondition(
        compute = null,
        os = null,
        ide = null,
        extension = listOf(
            ExtensionType(
                id = "aws.toolkit",
                version = NotificationExpression.NotEqualsCondition("1.3334")
            ),
            ExtensionType(
                id = "amazon.q",
                version = NotificationExpression.GreaterThanCondition("3.37.0")
            )
        ),
        authx = null

    ),
    actions = emptyList(),
    content = NotificationContentDescriptionLocale(
        NotificationContentDescription(
            title = "Look at this!",
            description = "Some bug is there"
        )
    )
)

val exampleNotificationWithoutSchema = """
        {
            "notifications": [
                {
                    "id": "notification-001",
                    "title": "Test Notification",
                    "description": "This is a test notification",
                    "type": "INFO",
   
                    "rules": {
                        "computeType": "Local",
                        "osType": "Linux",
                        "ideType": "IC",
                        "pluginVersion": {
                            "aws.toolkit": "1.0"
                        }
                    }
                }
            ]
        }
""".trimIndent()

val exampleNotificationWithoutNotification = """
        {
             "schema": {
    "version": "2.0"
}
            
        }
""".trimIndent()

val notificationWithoutConditionsOrActions = """
        {
        "id": "example_id_12344",
        "schedule": {
        "type": "StartUp"
    },
        "severity": "Critical",
         "content": {
        "en-US": {
        "title": "Look at this!",
        "description": "Some bug is there"
    }
    }
                }
               
           
""".trimIndent()

val notificationsWithoutConditionsOrActionsData = NotificationData(
    id = "example_id_12344",
    schedule = NotificationSchedule(type = "StartUp"),
    severity = "Critical",
    condition = null,
    actions = emptyList(),
    content = NotificationContentDescriptionLocale(
        NotificationContentDescription(
            title = "Look at this!",
            description = "Some bug is there"
        )
    )
)

val notificationWithConditionsOrActions = """
        {
        "id": "example_id_12344",
        "schedule": {
        "type": "StartUp"
    },
        "severity": "Critical",
        "condition": {
       "compute": {
        "type": {
        
            "==": "Local"
        
    }
    }
    },
        "actions": [
        {
            "type": "ShowMarketplace",
            "content": {
            "en-US": {
            "title": "Go to market"
        }
        }
        }
        ],
         "content": {
        "en-US": {
        "title": "Look at this!",
        "description": "Some bug is there"
    }
    }
                }
               
""".trimIndent()

val notificationWithConditionsOrActionsData = NotificationData(
    id = "example_id_12344",
    schedule = NotificationSchedule(type = "StartUp"),
    severity = "Critical",
    condition = NotificationDisplayCondition(
        compute = ComputeType(type = NotificationExpression.ComparisonCondition("Local"), architecture = null),
        os = null,
        ide = null,
        extension = null,
        authx = null
    ),
    actions = listOf(
        NotificationFollowupActions(
            type = "ShowMarketplace",
            content = NotificationFollowupActionsContent(
                NotificationActionDescription(
                    title = "Go to market",
                    url = null
                )
            )
        )
    ),
    content = NotificationContentDescriptionLocale(
        NotificationContentDescription(
            title = "Look at this!",
            description = "Some bug is there"
        )
    )
)

val notificationWithValidConnection = """{
    "id": "example_id_12344",
    "schedule": {
    "type": "StartUp"
},
    "severity": "Critical",
    "condition": {
   "authx": [{
            "feature" : "q",
        "type": {
        "anyOf": [
        "Idc",
        "BuilderId"
        ]
    },
        "region": {
        "==": "us-west-2"
    },
        "connectionState": {
        "==": "Connected"
    }
    } ]
},
    "content": {
    "en-US": {
    "title": "Look at this!",
    "description": "Some bug is there"
}
}
}
""".trimIndent()

val notificationWithValidConnectionData = NotificationData(
    id = "example_id_12344",
    schedule = NotificationSchedule(type = "StartUp"),
    severity = "Critical",
    condition = NotificationDisplayCondition(
        compute = null,
        os = null,
        ide = null,
        extension = null,
        authx = listOf(
            AuthxType(
                feature = "q",
                type = NotificationExpression.AnyOfCondition(listOf("Idc", "BuilderId")),
                region = NotificationExpression.ComparisonCondition("us-west-2"),
                connectionState = NotificationExpression.ComparisonCondition("Connected"),
                ssoScopes = null
            )
        )
    ),
    actions = emptyList(),
    content = NotificationContentDescriptionLocale(
        NotificationContentDescription(
            title = "Look at this!",
            description = "Some bug is there"
        )
    )
)

val invalidIdeTypeAndVersion = """{
    "id": "example_id_12344",
    "schedule": {
    "type": "StartUp"
},
    "severity": "Critical",
    "condition": {
    "ide": {
    "type": {"noneOf": ["IC","IU","RD"]},
    "version": {"!=": "1.3334"}
}
},
    "content": {
    "en-US": {
    "title": "Look at this!",
    "description": "Some bug is there"
}
}
}
""".trimIndent()

val invalidIdeTypeAndVersionData = NotificationData(
    id = "example_id_12344",
    schedule = NotificationSchedule(type = "StartUp"),
    severity = "Critical",
    condition = NotificationDisplayCondition(
        compute = null,
        os = null,
        ide = SystemType(
            type = NotificationExpression.NoneOfCondition(listOf("IC", "IU", "RD")),
            version = NotificationExpression.NotEqualsCondition("1.3334")
        ),
        extension = null,
        authx = null

    ),
    actions = emptyList(),
    content = NotificationContentDescriptionLocale(
        NotificationContentDescription(
            title = "Look at this!",
            description = "Some bug is there"
        )
    )
)

val pluginNotPresentData = NotificationData(
    id = "example_id_12344",
    schedule = NotificationSchedule(type = "StartUp"),
    severity = "Critical",
    condition = NotificationDisplayCondition(
        compute = null,
        os = null,
        ide = null,
        extension = mutableListOf(
            ExtensionType(
                "amazon.q",
                version = NotificationExpression.NotEqualsCondition("1.3334")
            )
        ),
        authx = null

    ),
    actions = emptyList(),
    content = NotificationContentDescriptionLocale(
        NotificationContentDescription(
            title = "Look at this!",
            description = "Some bug is there"
        )
    )
)
