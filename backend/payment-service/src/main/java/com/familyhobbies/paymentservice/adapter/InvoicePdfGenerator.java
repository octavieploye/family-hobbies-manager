package com.familyhobbies.paymentservice.adapter;

import com.familyhobbies.paymentservice.entity.Invoice;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Generates invoice PDFs using OpenPDF.
 * Returns byte arrays (in-memory only, no file storage for MVP).
 * French layout with seller/buyer info, line items, and totals.
 */
@Component
public class InvoicePdfGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
    private static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

    /**
     * Generates a PDF document for the given invoice.
     *
     * @param invoice the invoice entity with all data populated
     * @return the PDF as a byte array
     */
    public byte[] generatePdf(Invoice invoice) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);

        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            addTitle(document, invoice);
            addSellerBuyerSection(document, invoice);
            addLineItemsTable(document, invoice);
            addTotalsSection(document, invoice);
            addFooter(document);

        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate invoice PDF: " + e.getMessage(), e);
        } finally {
            document.close();
        }

        return outputStream.toByteArray();
    }

    private void addTitle(Document document, Invoice invoice) throws DocumentException {
        Paragraph title = new Paragraph("FACTURE", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph invoiceNumber = new Paragraph(
                "N\u00b0 " + safeString(invoice.getInvoiceNumber()), HEADER_FONT);
        invoiceNumber.setAlignment(Element.ALIGN_CENTER);
        invoiceNumber.setSpacingAfter(20);
        document.add(invoiceNumber);

        if (invoice.getIssuedAt() != null) {
            Paragraph date = new Paragraph(
                    "Date : " + invoice.getIssuedAt().format(DATE_FORMATTER), NORMAL_FONT);
            date.setAlignment(Element.ALIGN_RIGHT);
            document.add(date);
        }

        document.add(new Paragraph(" "));
    }

    private void addSellerBuyerSection(Document document, Invoice invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(20);

        // Seller column
        PdfPCell sellerCell = new PdfPCell();
        sellerCell.setBorder(PdfPCell.BOX);
        sellerCell.setPadding(10);
        sellerCell.addElement(new Paragraph("VENDEUR", HEADER_FONT));
        sellerCell.addElement(new Paragraph(safeString(invoice.getSellerName()), NORMAL_FONT));
        sellerCell.addElement(new Paragraph(safeString(invoice.getSellerAddress()), NORMAL_FONT));
        table.addCell(sellerCell);

        // Buyer column
        PdfPCell buyerCell = new PdfPCell();
        buyerCell.setBorder(PdfPCell.BOX);
        buyerCell.setPadding(10);
        buyerCell.addElement(new Paragraph("ACHETEUR", HEADER_FONT));
        buyerCell.addElement(new Paragraph(safeString(invoice.getBuyerName()), NORMAL_FONT));
        buyerCell.addElement(new Paragraph(safeString(invoice.getBuyerEmail()), NORMAL_FONT));
        buyerCell.addElement(new Paragraph(safeString(invoice.getBuyerAddress()), NORMAL_FONT));
        table.addCell(buyerCell);

        document.add(table);
    }

    private void addLineItemsTable(Document document, Invoice invoice) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{4, 1, 1, 1});
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);

        // Table headers
        addTableHeaderCell(table, "Description");
        addTableHeaderCell(table, "Montant HT");
        addTableHeaderCell(table, "TVA");
        addTableHeaderCell(table, "Montant TTC");

        // Single line item
        addTableCell(table, safeString(invoice.getDescription()));
        addTableCell(table, formatAmount(invoice.getAmount(), invoice.getCurrency()));
        addTableCell(table, invoice.getTaxRate() != null ? invoice.getTaxRate() + "%" : "0.00%");
        addTableCell(table, formatAmount(invoice.getTotalAmount(), invoice.getCurrency()));

        document.add(table);
    }

    private void addTotalsSection(Document document, Invoice invoice) throws DocumentException {
        PdfPTable totalsTable = new PdfPTable(2);
        totalsTable.setWidthPercentage(50);
        totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalsTable.setSpacingAfter(30);

        addTotalRow(totalsTable, "Sous-total HT :", formatAmount(invoice.getAmount(), invoice.getCurrency()));
        addTotalRow(totalsTable, "TVA :", formatAmount(invoice.getTaxAmount(), invoice.getCurrency()));
        addTotalRow(totalsTable, "Total TTC :", formatAmount(invoice.getTotalAmount(), invoice.getCurrency()));

        document.add(totalsTable);
    }

    private void addFooter(Document document) throws DocumentException {
        Paragraph footer = new Paragraph(
                "Family Hobbies Manager - Document g\u00e9n\u00e9r\u00e9 automatiquement", SMALL_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(40);
        document.add(footer);
    }

    private void addTableHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER_FONT));
        cell.setBackgroundColor(new Color(52, 73, 94));
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, NORMAL_FONT));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, HEADER_FONT));
        labelCell.setBorder(PdfPCell.NO_BORDER);
        labelCell.setPadding(4);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, HEADER_FONT));
        valueCell.setBorder(PdfPCell.NO_BORDER);
        valueCell.setPadding(4);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private String formatAmount(java.math.BigDecimal amount, String currency) {
        if (amount == null) {
            return "0.00 " + safeString(currency);
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP) + " " + safeString(currency);
    }
}
