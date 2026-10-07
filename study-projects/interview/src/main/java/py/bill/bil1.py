from fpdf import FPDF

# --- function to generate one receipt PDF ---
def generate_receipt(date_text):
    receipt_text = f"""Receipt no. 2071779-AE1125-379334
Date: {date_text}
Recipient:
bhanu pratap
Start: Al Barsha Road 1, Dubai ({date_text} 21:59)
Finish: {date_text} 22:22
Title Total sum
(AED)
Trip Fee 130.00
Toll Road 8.00
Dubai Taxi Booking Fee 4.00
Total (AED): 142.00
Paid by AE3Y4D8ZEDNDXV8-1: -20.00
Charged Card terminal: 122.00
This is a receipt, not an invoice."""

    pdf = FPDF()
    pdf.add_page()
    pdf.set_font("Arial", size=12)
    for line in receipt_text.split("\n"):
        pdf.cell(0, 10, txt=line, ln=True)

    # name of the new PDF output file
    file_name = f"travel_P_room-to-office_{date_text}.pdf"
    pdf.output(file_name)
    print(f"✅ Created: {file_name}")


# --- call function with a different date ---
generate_receipt("2025-12-01")