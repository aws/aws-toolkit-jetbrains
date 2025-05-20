/*!
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import { ChatItem, MynahIcons } from '@aws/mynah-ui-chat'

export const disclaimerAcknowledgeButtonId = 'amazonq-disclaimer-acknowledge-button-id'
export const disclaimerCard: Partial<ChatItem> = {
    messageId: 'amazonq-disclaimer-card',
    body: 'Amazon Q Developer uses generative AI. You may need to verify responses. See the [AWS Responsible AI Policy](https://aws.amazon.com/machine-learning/responsible-ai/policy/). Amazon Q may retain chats to provide and maintain the service. For information on the AWS Regions where Amazon Q may perform inference, see [the documentation](https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/cross-region-processing.html#cross-region-inference).',
    buttons: [
        {
            text: 'Acknowledge',
            id: disclaimerAcknowledgeButtonId,
            status: 'info',
            icon: MynahIcons.OK,
        },
    ],
}
