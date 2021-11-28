package corporateAcquisitionIR;

enum EntityType {
	ORGANIZATION,
	LOCATION,
	MONEY,
	STATUS;
	
	String toString(EntityType e) {
		switch(e) {
		case ORGANIZATION:
			return "organization";

		case LOCATION:
			return "location";

		case MONEY:
			return "money";

		case STATUS:
			return "status";
			
		default:
			return "[default toString()]";
		}
	}
}