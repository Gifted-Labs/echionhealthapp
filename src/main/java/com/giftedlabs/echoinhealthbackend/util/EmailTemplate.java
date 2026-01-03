package com.giftedlabs.echoinhealthbackend.util;

/**
 * HTML email templates for the application
 */
public class EmailTemplate {

    /**
     * Generate email verification HTML template
     * 
     * @param firstName        User's first name
     * @param verificationLink Full verification URL
     * @return HTML email content
     */
    public static String getVerificationEmail(String firstName, String verificationLink) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Verify Your Email - Echoin Health</title>
                </head>
                <body style="margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f4f4f4;">
                    <table role="presentation" style="width: 100%%; border-collapse: collapse;">
                        <tr>
                            <td align="center" style="padding: 40px 0;">
                                <table role="presentation" style="width: 600px; border-collapse: collapse; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                                    <!-- Header -->
                                    <tr>
                                        <td style="padding: 40px 40px 20px 40px; text-align: center; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); border-radius: 8px 8px 0 0;">
                                            <h1 style="margin: 0; color: #ffffff; font-size: 28px; font-weight: bold;">Echoin Health</h1>
                                            <p style="margin: 10px 0 0 0; color: #ffffff; font-size: 14px;">Ultrasound Report Management</p>
                                        </td>
                                    </tr>

                                    <!-- Content -->
                                    <tr>
                                        <td style="padding: 40px;">
                                            <h2 style="margin: 0 0 20px 0; color: #333333; font-size: 24px;">Welcome, %s!</h2>
                                            <p style="margin: 0 0 20px 0; color: #666666; font-size: 16px; line-height: 1.6;">
                                                Thank you for registering with Echoin Health. To complete your registration and start managing your ultrasound reports, please verify your email address.
                                            </p>

                                            <!-- CTA Button -->
                                            <table role="presentation" style="margin: 30px 0;">
                                                <tr>
                                                    <td style="text-align: center;">
                                                        <a href="%s" style="display: inline-block; padding: 14px 40px; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: #ffffff; text-decoration: none; border-radius: 6px; font-size: 16px; font-weight: bold;">
                                                            Verify Email Address
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>

                                            <p style="margin: 30px 0 10px 0; color: #666666; font-size: 14px; line-height: 1.6;">
                                                Or copy and paste this link into your browser:
                                            </p>
                                            <p style="margin: 0; padding: 15px; background-color: #f8f8f8; border-radius: 4px; word-break: break-all; font-size: 13px; color: #667eea;">
                                                %s
                                            </p>

                                            <p style="margin: 30px 0 0 0; color: #999999; font-size: 13px; line-height: 1.6;">
                                                This link will expire in 24 hours. If you didn't create an account with Echoin Health, please ignore this email.
                                            </p>
                                        </td>
                                    </tr>

                                    <!-- Footer -->
                                    <tr>
                                        <td style="padding: 30px 40px; background-color: #f8f8f8; border-radius: 0 0 8px 8px; text-align: center;">
                                            <p style="margin: 0; color: #999999; font-size: 12px;">
                                                © 2026 Echoin Health. All rights reserved.
                                            </p>
                                            <p style="margin: 10px 0 0 0; color: #999999; font-size: 12px;">
                                                HIPAA-Compliant Ultrasound Report Management System
                                            </p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """
                .formatted(firstName, verificationLink, verificationLink);
    }

    /**
     * Generate welcome email after successful verification
     * 
     * @param firstName User's first name
     * @return HTML email content
     */
    public static String getWelcomeEmail(String firstName) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Welcome to Echoin Health</title>
                </head>
                <body style="margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f4f4f4;">
                    <table role="presentation" style="width: 100%%; border-collapse: collapse;">
                        <tr>
                            <td align="center" style="padding: 40px 0;">
                                <table role="presentation" style="width: 600px; border-collapse: collapse; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                                    <tr>
                                        <td style="padding: 40px 40px 20px 40px; text-align: center; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); border-radius: 8px 8px 0 0;">
                                            <h1 style="margin: 0; color: #ffffff; font-size: 28px; font-weight: bold;">✓ Email Verified!</h1>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 40px;">
                                            <h2 style="margin: 0 0 20px 0; color: #333333; font-size: 24px;">Welcome to Echoin Health, %s!</h2>
                                            <p style="margin: 0 0 20px 0; color: #666666; font-size: 16px; line-height: 1.6;">
                                                Your email has been successfully verified. You can now access all features of the Echoin Health platform.
                                            </p>
                                            <p style="margin: 0 0 20px 0; color: #666666; font-size: 16px; line-height: 1.6;">
                                                Next steps:
                                            </p>
                                            <ul style="color: #666666; font-size: 16px; line-height: 1.8;">
                                                <li>Complete your professional profile</li>
                                                <li>Start uploading ultrasound reports</li>
                                                <li>Organize your reports with folders and tags</li>
                                            </ul>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 30px 40px; background-color: #f8f8f8; border-radius: 0 0 8px 8px; text-align: center;">
                                            <p style="margin: 0; color: #999999; font-size: 12px;">
                                                © 2026 Echoin Health. All rights reserved.
                                            </p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """
                .formatted(firstName);
    }
}
