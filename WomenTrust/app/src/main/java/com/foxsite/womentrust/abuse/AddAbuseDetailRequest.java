package com.foxsite.womentrust.abuse;

import java.util.ArrayList;

public class AddAbuseDetailRequest {

//    {
//        "Actions":"Abuse", "aBUSES_MASTER": [{
//        "UserName":"Admin",
//                "ViolenceName":"Domestic Violence",
//                "SubViolenceName":"Intimate Partner Violence",
//                "CategoryName":"Physical Abuse",
//                "SubCategoryName":["Abuse", "Fire"],
//        "Colour":"Red",
//                "ColourRating":"8",
//                "MASTER_STATUS":"Active",
//                "USERNAME":"Admin",
//                "ROLE":"Manager",
//                "UPDATED":"12-07-2025",
//                "UPDATETIME":"12-07-2025 10:04:00"
//    }
//    ]
//    }


    String UserName;
    String ViolenceName;
    String SubViolenceName;
    String CategoryName;
    ArrayList<String> SubCategoryName;
    String Colour;
    String ColourRating;
    String MASTER_STATUS;
    String USERNAME;
    String ROLE;
    String UPDATED;
    String UPDATETIME;


    public AddAbuseDetailRequest(String userName, String violenceName, String subViolenceName, String categoryName, ArrayList<String> subCategoryName, String colour, String colourRating, String MASTER_STATUS, String USERNAME, String ROLE, String UPDATED, String UPDATETIME) {
        UserName = userName;
        ViolenceName = violenceName;
        SubViolenceName = subViolenceName;
        CategoryName = categoryName;
        SubCategoryName = subCategoryName;
        Colour = colour;
        ColourRating = colourRating;
        this.MASTER_STATUS = MASTER_STATUS;
        this.USERNAME = USERNAME;
        this.ROLE = ROLE;
        this.UPDATED = UPDATED;
        this.UPDATETIME = UPDATETIME;
    }
}
