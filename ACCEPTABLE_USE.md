# ContactFlow Acceptable Use & Anti-Abuse Policy

ContactFlow is committed to ethical business intelligence and compliant communication practices.

## 1. Permitted Public Data Extraction
- ContactFlow only accesses publicly displayed business contact information on web pages that explicitly permit crawling.
- ContactFlow **strictly prohibits**:
  - Bypassing paywalls, authentication walls, or login gates.
  - Solving or evading CAPTCHAs.
  - Guessing or synthesizing private personal email addresses.
  - Crawling internal network systems or non-public services (enforced by SSRF validators).

## 2. Anti-Spam Compliance (CAN-SPAM, GDPR, CASL)
When communicating with business contacts discovered via ContactFlow:
1. **Accurate Header Information**: The 'From', 'To', and routing information must be accurate and identify your real organization.
2. **Clear Subject Lines**: Subject lines must accurately reflect the content of the message.
3. **Explicit Physical Address**: Commercial emails must include a valid physical postal address of the sender.
4. **Mandatory Unsubscribe Mechanism**: Every campaign email must include a clear, functional opt-out or unsubscribe mechanism.
5. **Prompt Honor of Opt-Out Requests**: ContactFlow provides an integrated Suppression List. Opt-out requests must be processed within 10 business days and immediately added to the suppression list.

## 3. Gmail API User Data Compliance
- ContactFlow requests only the `https://www.googleapis.com/auth/gmail.send` scope.
- We do not access, store, or read user inbox content.
- We do not share Google user data with third-party advertising platforms or AI model training sets.
